package com.entidaddesconocida.monte_erp.viewmodel;

import com.entidaddesconocida.monte_erp.domain.model.Category;
import com.entidaddesconocida.monte_erp.domain.model.Product;
import com.entidaddesconocida.monte_erp.domain.repository.CategoryRepository;
import com.entidaddesconocida.monte_erp.domain.repository.ProductRepository;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;

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
 * <p>The stock is out of scope: it is computed from the lots ({@code STOCK}) in
 * RF-09/RF-10. The category is optional: a product saved with an empty combo is
 * persisted with {@code category_id} {@code NULL}.</p>
 *
 * <p>Only the product specific parts live here: the field rules, the SKU
 * duplicate check, the inactive category rule (decision D4) and the labels. The
 * CRUD skeleton comes from {@link FilterableCrudViewModel} (search, inactive
 * toggle and category filter) and {@link CrudViewModel}.</p>
 */
public class ProductsViewModel extends FilterableCrudViewModel<Product> {

    /** Label of the destructive toggle action (logically delete the selected product). */
    public static final String TOGGLE_DEACTIVATE_LABEL = "Dar de baja producto";

    /** Label of the recovery toggle action (activate the selected product). */
    public static final String TOGGLE_ACTIVATE_LABEL = "Reactivar producto";

    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_SKU_LENGTH = 50;
    private static final BigDecimal MAX_PRICE = new BigDecimal("99999999.99");

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    private final StringProperty name = new SimpleStringProperty("");
    private final StringProperty sku = new SimpleStringProperty("");
    private final StringProperty price = new SimpleStringProperty("");
    private final ObjectProperty<Category> category = new SimpleObjectProperty<>();
    private final ObjectProperty<Category> categoryFilter = new SimpleObjectProperty<>();

    private final ObservableList<Category> categoryOptions = FXCollections.observableArrayList();
    private final ObservableList<Category> categoryFilterOptions = FXCollections.observableArrayList();

    private final ReadOnlyStringWrapper nameError = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper skuError = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper priceError = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper categoryError = new ReadOnlyStringWrapper("");

    public ProductsViewModel(ProductRepository productRepository,
                             CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        bindFilter(searchText, categoryFilter);
        watchFields(name, sku, price, category);
        updateCount();
    }

    /** Master list currently loaded (active or inactive products). */
    public ObservableList<Product> products() {
        return items;
    }

    /** Filtered view bound to the table. */
    public FilteredList<Product> filteredProducts() {
        return filteredItems;
    }

    /** Currently selected product, or {@code null} when none is selected. */
    public ObjectProperty<Product> selectedProductProperty() {
        return selectedItem;
    }

    public StringProperty nameProperty() {
        return name;
    }

    public StringProperty skuProperty() {
        return sku;
    }

    /** Price as typed by the user; parsed in the save template. */
    public StringProperty priceProperty() {
        return price;
    }

    /** Category chosen in the form; {@code null} means "sin categoría" (optional since RF-03). */
    public ObjectProperty<Category> categoryProperty() {
        return category;
    }

    /** Category filter; {@code null} means "every category". */
    public ObjectProperty<Category> categoryFilterProperty() {
        return categoryFilter;
    }

    /** Categories for the form combo: only the active ones (decision D4). */
    public ObservableList<Category> categoryOptions() {
        return categoryOptions;
    }

    /**
     * Categories for the filter combo: every category, active or not, so an
     * inactive one is still filterable (decision D5).
     */
    public ObservableList<Category> categoryFilterOptions() {
        return categoryFilterOptions;
    }

    /** Footer text with the number of products currently shown. */
    public ReadOnlyStringProperty productCountProperty() {
        return itemCountProperty();
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

    /**
     * Empty when the chosen category can be assigned; the message to show when
     * the product still holds a category that has been given up (decision D4).
     */
    public ReadOnlyStringProperty categoryErrorProperty() {
        return categoryError.getReadOnlyProperty();
    }

    /** Clears the form so a brand new product can be typed in. */
    public void newProduct() {
        newItem();
    }

    @Override
    protected void loadAuxiliaryData() throws SQLException {
        // The form only offers assignable (active) categories; the filter still
        // lists the ones given up so their products can be found (D4 / D5).
        categoryOptions.setAll(categoryRepository.findActive());
        categoryFilterOptions.setAll(categoryRepository.findAll());
    }

    @Override
    protected List<Product> loadActive() throws SQLException {
        return productRepository.findAll();
    }

    @Override
    protected List<Product> loadInactive() throws SQLException {
        return productRepository.findInactive();
    }

    @Override
    protected boolean matches(Product product) {
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

    @Override
    protected void write() throws SQLException {
        String nameValue = trimmed(name.get());
        String skuValue = trimmed(sku.get());
        BigDecimal priceValue = parsePrice(price.get());
        Product selected = selectedItem.get();
        if (selected == null) {
            Product product = new Product(nameValue, skuValue, null, priceValue, 0,
                    category.get(), true);
            productRepository.insert(product);
        } else {
            // Persist a detached copy: a failed write must never leave the
            // rendered row with values that are not in the database. The
            // description and min_stock of the edited row are preserved (they
            // are not part of the RF-03 form).
            Product updated = new Product(nameValue, skuValue, selected.getDescription(),
                    priceValue, selected.getMinStock(), category.get(), selected.isActive());
            updated.setId(selected.getId());
            productRepository.update(updated);
        }
    }

    @Override
    protected boolean validateBusinessRules() throws SQLException {
        // A deactivated product still reserves its SKU (UNIQUE is global), so
        // the duplicate check spans both states.
        String skuValue = trimmed(sku.get());
        Product selected = selectedItem.get();
        Product existing = productRepository.findBySku(skuValue);
        if (existing != null && (selected == null || existing.getId() != selected.getId())) {
            rejectDuplicateSku(skuValue);
            return false;
        }
        return true;
    }

    @Override
    protected void handleIntegrityViolation() {
        // The unique constraint on sku is the safety net if the check above
        // races with another insert.
        rejectDuplicateSku(trimmed(sku.get()));
    }

    @Override
    protected void doDeactivate(int id) throws SQLException {
        productRepository.deactivate(id);
    }

    @Override
    protected void doActivate(int id) throws SQLException {
        productRepository.activate(id);
    }

    @Override
    public boolean itemIsActive(Product item) {
        return item.isActive();
    }

    @Override
    protected int itemId(Product item) {
        return item.getId();
    }

    @Override
    protected void populateForm(Product product) {
        validateOnEdit = false;
        name.set(product.getName());
        sku.set(product.getSku());
        price.set(product.getPrice() == null ? "" : product.getPrice().toPlainString());
        category.set(optionForCategory(product.getCategory()));
        setFormTitle(editFormTitle());
        clearErrorMessage();
        clearFieldErrors();
    }

    @Override
    protected void clearForm() {
        validateOnEdit = false;
        name.set("");
        sku.set("");
        price.set("");
        category.set(null);
        setFormTitle(newFormTitle());
        clearFieldErrors();
        clearErrorMessage();
    }

    @Override
    protected void refreshFieldErrors() {
        nameError.set(validateName());
        skuError.set(validateSku());
        priceError.set(validatePrice());
        categoryError.set(validateCategory());
    }

    @Override
    protected void clearFieldErrors() {
        nameError.set("");
        skuError.set("");
        priceError.set("");
        categoryError.set("");
    }

    @Override
    protected boolean hasFieldErrors() {
        return !nameError.get().isBlank() || !skuError.get().isBlank()
                || !priceError.get().isBlank() || !categoryError.get().isBlank();
    }

    @Override
    protected String fieldErrorsSummary() {
        return joinedErrors(nameError.get(), skuError.get(), priceError.get(), categoryError.get());
    }

    @Override
    protected String countText() {
        return "Productos: " + filteredItems.size();
    }

    @Override
    protected String newFormTitle() {
        return "Nuevo producto";
    }

    @Override
    protected String editFormTitle() {
        return "Editar producto";
    }

    @Override
    public String deactivateLabel() {
        return TOGGLE_DEACTIVATE_LABEL;
    }

    @Override
    protected String activateLabel() {
        return TOGGLE_ACTIVATE_LABEL;
    }

    @Override
    protected String notSelectedMessage() {
        return "Selecciona un producto de la tabla.";
    }

    @Override
    protected String alreadyActiveMessage() {
        return "El producto ya está activo.";
    }

    @Override
    protected String alreadyInactiveMessage() {
        return "El producto ya está dado de baja.";
    }

    @Override
    protected String deactivatedMessage(Product product) {
        return "Producto '" + product.getName() + "' dado de baja.";
    }

    @Override
    protected String activatedMessage(Product product) {
        return "Producto '" + product.getName() + "' reactivado.";
    }

    @Override
    protected String saveSuccessMessage() {
        return "Producto guardado correctamente.";
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

    /**
     * An inactive category cannot be assigned to a product (decision D4). The
     * rule only fires when the product still holds one that was given up: the
     * category is optional, so clearing it is always a valid way out.
     */
    private String validateCategory() {
        Category selected = category.get();
        if (selected != null && !selected.isActive()) {
            return "La categoría seleccionada está dada de baja. Elige otra o déjala sin categoría.";
        }
        return "";
    }

    private void rejectDuplicateSku(String skuValue) {
        String message = "El SKU '" + skuValue + "' ya existe.";
        skuError.set(message);
        fail(message);
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

    private static BigDecimal parsePrice(String value) {
        return new BigDecimal(trimmed(value).replace(',', '.')).setScale(2, RoundingMode.HALF_UP);
    }
}
