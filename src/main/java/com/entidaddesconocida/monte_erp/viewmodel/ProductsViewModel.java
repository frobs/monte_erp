package com.entidaddesconocida.monte_erp.viewmodel;

import com.entidaddesconocida.monte_erp.domain.model.Category;
import com.entidaddesconocida.monte_erp.domain.model.Product;
import com.entidaddesconocida.monte_erp.domain.model.ProductSupplier;
import com.entidaddesconocida.monte_erp.domain.model.Supplier;
import com.entidaddesconocida.monte_erp.domain.repository.CategoryRepository;
import com.entidaddesconocida.monte_erp.domain.repository.ProductRepository;
import com.entidaddesconocida.monte_erp.domain.repository.PurchaseRepository;
import com.entidaddesconocida.monte_erp.domain.repository.SupplierRepository;

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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ViewModel of the product management screen (RF-03 / CU-03, with the search
 * included from RF-11 / CU-10).
 *
 * <p>Holds the product list, the in-memory filters (text over name/SKU, category
 * and supplier combos) and the edition form. Products are disabled logically,
 * never physically deleted, because {@code STOCK} and {@code SALES_ORDER_LINE}
 * reference them with {@code ON DELETE RESTRICT}.</p>
 *
 * <p>The supplier is not a product attribute (it belongs to the purchase), so the
 * form does not offer it. The <strong>filter</strong> by supplier is fed from the
 * purchase/lot history (RF-14): {@link PurchaseRepository#findProductSuppliers()}
 * is loaded and grouped into {@code productId → supplierIds}. A product matches a
 * supplier when it has at least one lot of a purchase from that supplier, even if
 * the lot is at 0 (decision D3, the full history). The combo lists
 * {@code SupplierRepository.findAll()}, so an inactive supplier is still
 * filterable (decision D2, parallel to the category rule D5).</p>
 *
 * <p>The text search is case- and accent-insensitive through {@link SearchText}
 * (decision D5), so it matches the {@code utf8mb4_unicode_ci} collation of the
 * database: "cafe" finds "Café".</p>
 *
 * <p>The stock is out of scope: it is computed from the lots ({@code STOCK}) in
 * RF-09/RF-10. The category is optional: a product saved with an empty combo is
 * persisted with {@code category_id} {@code NULL}.</p>
 *
 * <p>Only the product specific parts live here: the field rules, the SKU
 * duplicate check, the inactive category rule (decision D4) and the labels. The
 * CRUD skeleton comes from {@link FilterableCrudViewModel} (search, inactive
 * toggle and category/supplier filters) and {@link CrudViewModel}.</p>
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
    private final SupplierRepository supplierRepository;
    private final PurchaseRepository purchaseRepository;

    private final StringProperty name = new SimpleStringProperty("");
    private final StringProperty sku = new SimpleStringProperty("");
    private final StringProperty price = new SimpleStringProperty("");
    private final StringProperty minStock = new SimpleStringProperty("");
    private final ObjectProperty<Category> category = new SimpleObjectProperty<>();
    private final ObjectProperty<Category> categoryFilter = new SimpleObjectProperty<>();
    private final ObjectProperty<Supplier> supplierFilter = new SimpleObjectProperty<>();

    private final ObservableList<Category> categoryOptions = FXCollections.observableArrayList();
    private final ObservableList<Category> categoryFilterOptions = FXCollections.observableArrayList();
    private final ObservableList<Supplier> supplierFilterOptions = FXCollections.observableArrayList();

    /** Suppliers of each product, from the purchase history (decision D3). */
    private final Map<Integer, Set<Integer>> supplierIdsByProduct = new HashMap<>();

    private final ReadOnlyStringWrapper nameError = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper skuError = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper priceError = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper minStockError = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper categoryError = new ReadOnlyStringWrapper("");

    public ProductsViewModel(ProductRepository productRepository,
                             CategoryRepository categoryRepository,
                             SupplierRepository supplierRepository,
                             PurchaseRepository purchaseRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.supplierRepository = supplierRepository;
        this.purchaseRepository = purchaseRepository;
        bindFilter(searchText, categoryFilter, supplierFilter);
        watchFields(name, sku, price, minStock, category);
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

    /** Minimum stock typed by the user (RF-10); empty means 0. */
    public StringProperty minStockProperty() {
        return minStock;
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

    /** Supplier filter; {@code null} means "every supplier". */
    public ObjectProperty<Supplier> supplierFilterProperty() {
        return supplierFilter;
    }

    /**
     * Suppliers for the filter combo: every supplier, active or not, so an
     * inactive one is still filterable (decision D2/D5, via
     * {@code SupplierRepository.findAll()}).
     */
    public ObservableList<Supplier> supplierFilterOptions() {
        return supplierFilterOptions;
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

    /** Empty when the minimum stock is valid; the message to show otherwise (RF-10). */
    public ReadOnlyStringProperty minStockErrorProperty() {
        return minStockError.getReadOnlyProperty();
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

    /**
     * Resets every filter to its default so the screen looks as if it had just
     * been opened (decision D6): empty search text, no category, no supplier and
     * "Mostrar inactivos" unchecked. The checkbox is set <strong>last</strong>
     * because it is the only one that triggers a reload, and only when it was
     * checked (the listeners of the other filters update the counter).
     */
    public void clearFilters() {
        searchText.set("");
        categoryFilter.set(null);
        supplierFilter.set(null);
        showInactive.set(false);
    }

    @Override
    protected void loadAuxiliaryData() throws SQLException {
        // The form only offers assignable (active) categories; the filter still
        // lists the ones given up so their products can be found (D4 / D5).
        categoryOptions.setAll(categoryRepository.findActive());
        categoryFilterOptions.setAll(categoryRepository.findAll());
        // The supplier filter lists every supplier (active or not, D2) and the
        // product↔supplier map comes from the whole purchase history (D3).
        supplierFilterOptions.setAll(supplierRepository.findAll());
        supplierIdsByProduct.clear();
        for (ProductSupplier pair : purchaseRepository.findProductSuppliers()) {
            supplierIdsByProduct
                    .computeIfAbsent(pair.getProductId(), key -> new HashSet<>())
                    .add(pair.getSupplierId());
        }
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
        // Case- and accent-insensitive over name/SKU (decision D5).
        if (!SearchText.matches(searchText.get(), product.getName(), product.getSku())) {
            return false;
        }
        Category selectedCategory = categoryFilter.get();
        if (selectedCategory != null && (product.getCategory() == null
                || product.getCategory().getId() != selectedCategory.getId())) {
            return false;
        }
        Supplier selectedSupplier = supplierFilter.get();
        return selectedSupplier == null || supplierIdsByProduct
                .getOrDefault(product.getId(), Set.of())
                .contains(selectedSupplier.getId());
    }

    @Override
    protected void write() throws SQLException {
        String nameValue = trimmed(name.get());
        String skuValue = trimmed(sku.get());
        BigDecimal priceValue = parsePrice(price.get());
        int minStockValue = parseMinStock(minStock.get());
        Product selected = selectedItem.get();
        if (selected == null) {
            Product product = new Product(nameValue, skuValue, null, priceValue, minStockValue,
                    category.get(), true);
            productRepository.insert(product);
        } else {
            // Persist a detached copy: a failed write must never leave the
            // rendered row with values that are not in the database. The
            // description of the edited row is preserved (it is not part of the
            // RF-03 form).
            Product updated = new Product(nameValue, skuValue, selected.getDescription(),
                    priceValue, minStockValue, category.get(), selected.isActive());
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
        minStock.set(String.valueOf(product.getMinStock()));
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
        minStock.set("");
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
        minStockError.set(validateMinStock());
        categoryError.set(validateCategory());
    }

    @Override
    protected void clearFieldErrors() {
        nameError.set("");
        skuError.set("");
        priceError.set("");
        minStockError.set("");
        categoryError.set("");
    }

    @Override
    protected boolean hasFieldErrors() {
        return !nameError.get().isBlank() || !skuError.get().isBlank()
                || !priceError.get().isBlank() || !minStockError.get().isBlank()
                || !categoryError.get().isBlank();
    }

    @Override
    protected String fieldErrorsSummary() {
        return joinedErrors(nameError.get(), skuError.get(), priceError.get(),
                minStockError.get(), categoryError.get());
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
     * Minimum stock is optional: empty means 0. When typed it must be a
     * non-negative integer, mirroring {@code chk_product_min_stock} (RF-10).
     */
    private String validateMinStock() {
        String value = trimmed(minStock.get());
        if (value.isEmpty()) {
            return "";
        }
        int parsed;
        try {
            parsed = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return "El stock mínimo debe ser un número entero.";
        }
        if (parsed < 0) {
            return "El stock mínimo no puede ser negativo.";
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

    private static int parseMinStock(String value) {
        String trimmedValue = trimmed(value);
        return trimmedValue.isEmpty() ? 0 : Integer.parseInt(trimmedValue);
    }
}
