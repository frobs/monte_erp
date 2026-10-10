package com.entidaddesconocida.monte_erp.viewmodel;

import com.entidaddesconocida.monte_erp.domain.model.Category;
import com.entidaddesconocida.monte_erp.domain.model.Product;
import com.entidaddesconocida.monte_erp.domain.repository.CategoryRepository;
import com.entidaddesconocida.monte_erp.domain.repository.ProductRepository;

import de.saxsys.mvvmfx.ViewModel;

import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ViewModel of the product management screen (RF-03 / CU-03, with the search
 * included from RF-11 / CU-10).
 *
 * <p>Holds the product list, the in-memory filters (text over name/SKU and the
 * category combo) and the edition form. Products are disabled logically, never
 * physically deleted, because {@code STOCK} and {@code SALES_ORDER_LINE}
 * reference them with {@code ON DELETE RESTRICT}.</p>
 *
 * <p>The supplier is not a product attribute (it belongs to the purchase), so the
 * form and the filters do not offer it. Filtering a product by supplier would
 * need the purchase/lot history (RF-14) and is out of scope here.</p>
 *
 * <p>Deliberately free of ValidatorFX: {@code new Validator()} needs the JavaFX
 * toolkit ({@code DefaultDecoration} loads an {@code Image} in its static
 * initializer), so the framework only lives in the View. Here the validation
 * rules are exposed as one error property per field, which makes them testable
 * with plain JUnit and no toolkit.</p>
 *
 * <p>The stock is out of scope: it is computed from the lots ({@code STOCK}) in
 * RF-09/RF-10. The category is optional: a product saved with an empty combo is
 * persisted with {@code category_id} {@code NULL}.</p>
 */
public class ProductsViewModel implements ViewModel {

    private static final Logger LOGGER = Logger.getLogger(ProductsViewModel.class.getName());

    /** Label of the destructive toggle action (logically delete the selected product). */
    public static final String TOGGLE_DEACTIVATE_LABEL = "Dar de baja producto";

    /** Label of the recovery toggle action (activate the selected product). */
    public static final String TOGGLE_ACTIVATE_LABEL = "Reactivar producto";

    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_SKU_LENGTH = 50;
    private static final BigDecimal MAX_PRICE = new BigDecimal("99999999.99");
    private static final String DATABASE_ERROR =
            "No se pudo conectar con la base de datos. Comprueba que MySQL esté arrancado.";
    private static final String RELOAD_ERROR =
            "Los cambios se guardaron, pero no se pudo recargar la lista. Pulsa 'Actualizar'.";

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    private final ObservableList<Product> products = FXCollections.observableArrayList();
    private final FilteredList<Product> filteredProducts = new FilteredList<>(products);
    private final ObjectProperty<Product> selectedProduct = new SimpleObjectProperty<>();

    private final StringProperty name = new SimpleStringProperty("");
    private final StringProperty sku = new SimpleStringProperty("");
    private final StringProperty price = new SimpleStringProperty("");
    private final ObjectProperty<Category> category = new SimpleObjectProperty<>();

    private final StringProperty searchText = new SimpleStringProperty("");
    private final ObjectProperty<Category> categoryFilter = new SimpleObjectProperty<>();
    private final BooleanProperty showInactive = new SimpleBooleanProperty(false);

    private final ObservableList<Category> categoryOptions = FXCollections.observableArrayList();

    private final StringProperty errorMessage = new SimpleStringProperty("");
    private final StringProperty statusMessage = new SimpleStringProperty("");
    private final ReadOnlyStringWrapper formTitle = new ReadOnlyStringWrapper("Nuevo producto");
    private final ReadOnlyStringWrapper toggleLabel =
            new ReadOnlyStringWrapper(TOGGLE_DEACTIVATE_LABEL);
    private final ReadOnlyBooleanWrapper toggleDisabled = new ReadOnlyBooleanWrapper(true);
    private final ReadOnlyStringWrapper productCount = new ReadOnlyStringWrapper("");

    private final ReadOnlyStringWrapper nameError = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper skuError = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper priceError = new ReadOnlyStringWrapper("");

    /** {@code true} only after the first save attempt, so fields are not red while typing the first time. */
    private boolean validateOnEdit;

    /** Guards the checkbox rollback so reverting does not fire the reload listener again. */
    private boolean revertingShowInactive;

    public ProductsViewModel(ProductRepository productRepository,
                             CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;

        // Selecting a row loads it into the form; clearing the selection resets
        // the form so a stale row can never be edited/saved by accident.
        selectedProduct.addListener((observable, oldValue, newValue) -> {
            if (newValue == null) {
                clearForm();
                return;
            }
            populateForm(newValue);
        });

        // Live re-validation once the user has already tried to save. The summary
        // label is refreshed too, otherwise the combined message of the failed
        // save stays visible after the user has corrected the fields.
        ChangeListener<Object> fieldChange = (observable, oldValue, newValue) -> {
            if (validateOnEdit) {
                refreshFieldErrors();
                errorMessage.set(hasFieldErrors() ? joinedFieldErrors() : "");
            }
        };
        name.addListener(fieldChange);
        sku.addListener(fieldChange);
        price.addListener(fieldChange);
        category.addListener(fieldChange);

        // Filters run in memory over the loaded list; FilteredList lives in
        // javafx.base, so the ViewModel stays testable without the toolkit.
        filteredProducts.predicateProperty().bind(Bindings.createObjectBinding(
                () -> this::matches,
                searchText, categoryFilter));

        products.addListener((ListChangeListener<Product>) change -> updateCount());
        filteredProducts.addListener((ListChangeListener<Product>) change -> updateCount());

        // Active and inactive rows come from different queries, so toggling the
        // checkbox reloads the master list. If the reload fails the checkbox is
        // rolled back, so it never disagrees with the rows still on screen.
        showInactive.addListener((observable, oldValue, newValue) -> {
            if (revertingShowInactive) {
                return;
            }
            try {
                refreshList();
            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, "Could not reload the products", e);
                revertShowInactive(oldValue);
                fail(DATABASE_ERROR);
            }
        });

        updateCount();
    }

    /** Master list currently loaded (active or inactive products). */
    public ObservableList<Product> products() {
        return products;
    }

    /** Filtered view bound to the table. */
    public FilteredList<Product> filteredProducts() {
        return filteredProducts;
    }

    /** Currently selected product, or {@code null} when none is selected. */
    public ObjectProperty<Product> selectedProductProperty() {
        return selectedProduct;
    }

    public StringProperty nameProperty() {
        return name;
    }

    public StringProperty skuProperty() {
        return sku;
    }

    /** Price as typed by the user; parsed in {@link #save()}. */
    public StringProperty priceProperty() {
        return price;
    }

    /** Category chosen in the form; {@code null} means "sin categoría" (optional since RF-03). */
    public ObjectProperty<Category> categoryProperty() {
        return category;
    }

    /** Free text matched case-insensitively against name and SKU. */
    public StringProperty searchTextProperty() {
        return searchText;
    }

    /** Category filter; {@code null} means "every category". */
    public ObjectProperty<Category> categoryFilterProperty() {
        return categoryFilter;
    }

    /** {@code true} to list logically deleted products instead of active ones. */
    public BooleanProperty showInactiveProperty() {
        return showInactive;
    }

    /** Categories for the form combo and the filter (loaded in {@link #load()}). */
    public ObservableList<Category> categoryOptions() {
        return categoryOptions;
    }

    public StringProperty errorMessageProperty() {
        return errorMessage;
    }

    public StringProperty statusMessageProperty() {
        return statusMessage;
    }

    /** {@code "Nuevo producto"} or {@code "Editar producto"}, depending on the selection. */
    public ReadOnlyStringProperty formTitleProperty() {
        return formTitle.getReadOnlyProperty();
    }

    /** Label of the activate/deactivate button, derived from the selected product. */
    public ReadOnlyStringProperty toggleLabelProperty() {
        return toggleLabel.getReadOnlyProperty();
    }

    /** {@code true} when the activate/deactivate button must be disabled. */
    public ReadOnlyBooleanProperty toggleDisabledProperty() {
        return toggleDisabled.getReadOnlyProperty();
    }

    /** Footer text with the number of products currently shown. */
    public ReadOnlyStringProperty productCountProperty() {
        return productCount.getReadOnlyProperty();
    }

    /** Empty when the name is valid; the message to show otherwise. */
    public ReadOnlyStringProperty nameErrorProperty() {
        return nameError.getReadOnlyProperty();
    }

    /** Empty when the SKU is valid; the message to show otherwise. */
    public ReadOnlyStringProperty skuErrorProperty() {
        return skuError.getReadOnlyProperty();
    }

    /** Empty when the price is valid; the message to show otherwise. */
    public ReadOnlyStringProperty priceErrorProperty() {
        return priceError.getReadOnlyProperty();
    }

    /** Loads the combo options and the product list, and resets the form. */
    public void load() {
        errorMessage.set("");
        statusMessage.set("");
        try {
            refreshOptions();
            refreshList();
            resetForm();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Could not load the products", e);
            errorMessage.set(DATABASE_ERROR);
        }
    }

    /** Clears the form so a brand new product can be typed in. */
    public void newProduct() {
        errorMessage.set("");
        statusMessage.set("");
        resetForm();
    }

    /**
     * Creates the product typed in the form, or updates the selected one. The
     * {@code description} and {@code min_stock} of an edited row are preserved
     * (they are not part of the RF-03 form).
     */
    public void save() {
        validateOnEdit = true;
        refreshFieldErrors();
        if (hasFieldErrors()) {
            fail(joinedFieldErrors());
            return;
        }

        String nameValue = trimmed(name.get());
        String skuValue = trimmed(sku.get());
        BigDecimal priceValue = parsePrice(price.get());
        Product selected = selectedProduct.get();

        try {
            // A deactivated product still reserves its SKU (UNIQUE is global),
            // so the duplicate check spans both states.
            if (skuAlreadyTaken(skuValue, selected)) {
                rejectDuplicateSku(skuValue);
                return;
            }
            if (selected == null) {
                Product product = new Product(nameValue, skuValue, null, priceValue, 0,
                        category.get(), true);
                productRepository.insert(product);
            } else {
                // Persist a detached copy: a failed write must never leave the
                // rendered row with values that are not in the database.
                Product updated = new Product(nameValue, skuValue, selected.getDescription(),
                        priceValue, selected.getMinStock(), category.get(), selected.isActive());
                updated.setId(selected.getId());
                productRepository.update(updated);
            }
        } catch (SQLIntegrityConstraintViolationException e) {
            // The unique constraint on sku is the safety net if the check above
            // races with another insert.
            LOGGER.log(Level.WARNING, "Duplicated SKU on save", e);
            rejectDuplicateSku(skuValue);
            return;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Could not save the product", e);
            fail(DATABASE_ERROR);
            return;
        }
        reloadAfterWrite("Producto guardado correctamente.");
    }

    /** Logical deletion of the selected product (never a physical delete). */
    public void deactivate() {
        Product selected = selectedProduct.get();
        if (selected == null) {
            fail("Selecciona un producto de la tabla.");
            return;
        }
        if (!selected.isActive()) {
            fail("El producto ya está dado de baja.");
            return;
        }
        try {
            productRepository.deactivate(selected.getId());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Could not deactivate the product", e);
            fail(DATABASE_ERROR);
            return;
        }
        reloadAfterWrite("Producto '" + selected.getName() + "' dado de baja.");
    }

    /** Brings a previously deleted product back to active. */
    public void activate() {
        Product selected = selectedProduct.get();
        if (selected == null) {
            fail("Selecciona un producto de la tabla.");
            return;
        }
        if (selected.isActive()) {
            fail("El producto ya está activo.");
            return;
        }
        try {
            productRepository.activate(selected.getId());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Could not activate the product", e);
            fail(DATABASE_ERROR);
            return;
        }
        reloadAfterWrite("Producto '" + selected.getName() + "' reactivado.");
    }

    private void refreshOptions() throws SQLException {
        categoryOptions.setAll(categoryRepository.findAll());
    }

    private void refreshList() throws SQLException {
        List<Product> loaded = showInactive.get()
                ? productRepository.findInactive()
                : productRepository.findAll();
        Product selected = selectedProduct.get();
        products.setAll(loaded);
        // A reload can drop the selected row (e.g. it moved to the other
        // active/inactive list): the form must not keep editing a row that is
        // no longer shown.
        if (selected != null && products.stream().noneMatch(product -> product.getId() == selected.getId())) {
            selectedProduct.set(null);
        }
    }

    /** Rolls the visibility checkbox back without firing the listener again. */
    private void revertShowInactive(boolean previousValue) {
        revertingShowInactive = true;
        try {
            showInactive.set(previousValue);
        } finally {
            revertingShowInactive = false;
        }
    }

    /**
     * Reloads the list after a confirmed write. The write is already committed,
     * so a reload failure must not be reported as a failed save: it gets its own
     * message and the form is still reset.
     */
    private void reloadAfterWrite(String successMessage) {
        try {
            refreshList();
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "The write succeeded but the reload failed", e);
            resetForm();
            fail(RELOAD_ERROR);
            return;
        }
        resetForm();
        succeed(successMessage);
    }

    private boolean skuAlreadyTaken(String candidate, Product edited) throws SQLException {
        Product existing = productRepository.findBySku(candidate);
        return existing != null && (edited == null || existing.getId() != edited.getId());
    }

    private void populateForm(Product product) {
        validateOnEdit = false;
        name.set(product.getName());
        sku.set(product.getSku());
        price.set(product.getPrice() == null ? "" : product.getPrice().toPlainString());
        category.set(optionForCategory(product.getCategory()));
        formTitle.set("Editar producto");
        errorMessage.set("");
        clearFieldErrors();
        refreshToggleState();
    }

    /** Clears the selection and the form; the listener ends up in {@link #clearForm()}. */
    private void resetForm() {
        selectedProduct.set(null);
        clearForm();
    }

    /** Clears the form fields and returns to the "new product" defaults. */
    private void clearForm() {
        validateOnEdit = false;
        name.set("");
        sku.set("");
        price.set("");
        category.set(null);
        formTitle.set("Nuevo producto");
        clearFieldErrors();
        errorMessage.set("");
        refreshToggleState();
    }

    /**
     * Recomputes the three field errors. Called on every save and, once the user
     * has tried to save, on every field change. The category is optional since
     * RF-03, so it has no rule: {@code null} means "sin categoría".
     */
    private void refreshFieldErrors() {
        nameError.set(validateName());
        skuError.set(validateSku());
        priceError.set(validatePrice());
    }

    private void clearFieldErrors() {
        nameError.set("");
        skuError.set("");
        priceError.set("");
    }

    private boolean hasFieldErrors() {
        return !nameError.get().isBlank() || !skuError.get().isBlank()
                || !priceError.get().isBlank();
    }

    private String joinedFieldErrors() {
        return List.of(nameError.get(), skuError.get(), priceError.get())
                .stream()
                .filter(message -> !message.isBlank())
                .reduce((first, second) -> first + "\n" + second)
                .orElse("");
    }

    private String validateName() {
        String value = trimmed(name.get());
        if (value.isEmpty()) {
            return "Introduce el nombre del producto.";
        }
        if (value.length() > MAX_NAME_LENGTH) {
            return "El nombre no puede superar los " + MAX_NAME_LENGTH + " caracteres.";
        }
        return "";
    }

    private String validateSku() {
        String value = trimmed(sku.get());
        if (value.isEmpty()) {
            return "Introduce el SKU del producto.";
        }
        if (value.length() > MAX_SKU_LENGTH) {
            return "El SKU no puede superar los " + MAX_SKU_LENGTH + " caracteres.";
        }
        return "";
    }

    private String validatePrice() {
        String value = trimmed(price.get());
        if (value.isEmpty()) {
            return "Introduce el precio del producto.";
        }
        BigDecimal parsed;
        try {
            parsed = new BigDecimal(value.replace(',', '.'));
        } catch (NumberFormatException e) {
            return "El precio debe ser un número válido.";
        }
        if (parsed.signum() < 0) {
            return "El precio no puede ser negativo.";
        }
        if (parsed.compareTo(MAX_PRICE) > 0) {
            return "El precio no puede superar 99999999.99.";
        }
        return "";
    }

    private void rejectDuplicateSku(String skuValue) {
        String message = duplicateMessage(skuValue);
        skuError.set(message);
        fail(message);
    }

    private boolean matches(Product product) {
        String text = trimmed(searchText.get()).toLowerCase(Locale.ROOT);
        if (!text.isEmpty()) {
            boolean nameMatches = product.getName() != null
                    && product.getName().toLowerCase(Locale.ROOT).contains(text);
            boolean skuMatches = product.getSku() != null
                    && product.getSku().toLowerCase(Locale.ROOT).contains(text);
            if (!nameMatches && !skuMatches) {
                return false;
            }
        }
        Category selectedCategory = categoryFilter.get();
        return selectedCategory == null || (product.getCategory() != null
                && product.getCategory().getId() == selectedCategory.getId());
    }

    /**
     * Returns the combo option matching the given category by id, falling back
     * to the embedded one (id + name) when the option is not loaded yet.
     * Matching by id keeps the combo selection stable across reloads.
     */
    private Category optionForCategory(Category productCategory) {
        if (productCategory == null) {
            return null;
        }
        return categoryOptions.stream()
                .filter(option -> option.getId() == productCategory.getId())
                .findFirst()
                .orElse(productCategory);
    }

    private void refreshToggleState() {
        Product selected = selectedProduct.get();
        if (selected == null || selected.isActive()) {
            toggleLabel.set(TOGGLE_DEACTIVATE_LABEL);
            toggleDisabled.set(selected == null);
        } else {
            toggleLabel.set(TOGGLE_ACTIVATE_LABEL);
            toggleDisabled.set(false);
        }
    }

    private void updateCount() {
        productCount.set("Productos: " + filteredProducts.size());
    }

    private void fail(String message) {
        statusMessage.set("");
        errorMessage.set(message);
    }

    private void succeed(String message) {
        errorMessage.set("");
        statusMessage.set(message);
    }

    private static String duplicateMessage(String sku) {
        return "El SKU '" + sku + "' ya existe.";
    }

    private static BigDecimal parsePrice(String value) {
        return new BigDecimal(trimmed(value).replace(',', '.')).setScale(2, RoundingMode.HALF_UP);
    }

    private static String trimmed(String value) {
        return value == null ? "" : value.trim();
    }
}
