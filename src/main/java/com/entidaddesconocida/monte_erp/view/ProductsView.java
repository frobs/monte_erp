package com.entidaddesconocida.monte_erp.view;

import com.entidaddesconocida.monte_erp.app.AppShell;
import com.entidaddesconocida.monte_erp.domain.model.Category;
import com.entidaddesconocida.monte_erp.domain.model.Product;
import com.entidaddesconocida.monte_erp.domain.model.Supplier;
import com.entidaddesconocida.monte_erp.viewmodel.Amounts;
import com.entidaddesconocida.monte_erp.viewmodel.CrudViewModel;
import com.entidaddesconocida.monte_erp.viewmodel.ProductsViewModel;

import de.saxsys.mvvmfx.FxmlView;
import de.saxsys.mvvmfx.InjectViewModel;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

/**
 * Code-behind of the product management FXML (RF-03).
 *
 * <p>Binds the table, the filters and the form to the
 * {@link ProductsViewModel} and delegates navigation to the {@link AppShell}.
 * The CRUD skeleton (selection synchronization, messages, toggle, ValidatorFX
 * decoration and the destructive confirmation) comes from {@link CrudView}; only
 * the product specific controls (filters, combos and cell formatting) live
 * here.</p>
 */
public class ProductsView extends CrudView<Product> implements FxmlView<ProductsViewModel> {

    private static final String ALL_CATEGORIES = "Todas las categorías";

    private static final String ALL_SUPPLIERS = "Todos los proveedores";

    private static final String NO_CATEGORY = "Sin categoría";

    /** 99999999.99 (the ViewModel range) has eight integer digits. */
    private static final int PRICE_MAX_DIGITS = 8;

    /** Keeps the typed minimum stock within the {@code int} range (RF-10). */
    private static final int MIN_STOCK_MAX_DIGITS = 9;

    @FXML
    private TableView<Product> productsTable;

    @FXML
    private TableColumn<Product, String> skuColumn;

    @FXML
    private TableColumn<Product, String> nameColumn;

    @FXML
    private TableColumn<Product, String> priceColumn;

    @FXML
    private TableColumn<Product, String> categoryColumn;

    @FXML
    private TableColumn<Product, String> statusColumn;

    @FXML
    private TextField searchField;

    @FXML
    private ComboBox<Category> categoryFilterCombo;

    @FXML
    private ComboBox<Supplier> supplierFilterCombo;

    @FXML
    private CheckBox showInactiveCheck;

    @FXML
    private TextField nameField;

    @FXML
    private TextField skuField;

    @FXML
    private TextField descriptionField;

    @FXML
    private TextField priceField;

    @FXML
    private TextField minStockField;

    @FXML
    private ComboBox<Category> categoryCombo;

    @FXML
    private Label formTitleLabel;

    @FXML
    private Label errorLabel;

    @FXML
    private Label statusLabel;

    @FXML
    private Label countLabel;

    @FXML
    private Button toggleActiveButton;

    @InjectViewModel
    private ProductsViewModel viewModel;

    /** Form combo contents: the "sin categoría" option plus the active categories (and the product's own one when it is inactive). */
    private final ObservableList<Category> formCategoryItems = FXCollections.observableArrayList();

    public ProductsView(AppShell appShell) {
        super(appShell);
    }

    @Override
    protected CrudViewModel<Product> viewModel() {
        return viewModel;
    }

    @FXML
    private void initialize() {
        productsTable.setItems(viewModel.filteredProducts());
        initCommon();
    }

    @Override
    protected void setupFields() {
        skuColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getSku()));
        nameColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getName()));
        priceColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(Amounts.currency(cell.getValue().getPrice())));
        categoryColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(categoryName(cell.getValue())));
        statusColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getStatusLabel()));

        searchField.textProperty().bindBidirectional(viewModel.searchTextProperty());
        showInactiveCheck.selectedProperty().bindBidirectional(viewModel.showInactiveProperty());

        nameField.textProperty().bindBidirectional(viewModel.nameProperty());
        skuField.textProperty().bindBidirectional(viewModel.skuProperty());
        descriptionField.textProperty().bindBidirectional(viewModel.descriptionProperty());
        priceField.textProperty().bindBidirectional(viewModel.priceProperty());
        // Local input mask: letters and signs never reach the ViewModel (RF-03).
        priceField.setTextFormatter(InputMasks.money(PRICE_MAX_DIGITS));

        minStockField.textProperty().bindBidirectional(viewModel.minStockProperty());
        // Optional, but when typed it must be a non-negative integer (RF-10).
        minStockField.setTextFormatter(InputMasks.positiveInteger(MIN_STOCK_MAX_DIGITS));

        countLabel.textProperty().bind(viewModel.productCountProperty());

        // The form combo lists the assignable categories plus (only while such a
        // product is being edited) the inactive one the product already holds,
        // marked with its suffix; the ViewModel blocks saving it (decision D4).
        categoryCombo.setItems(formCategoryItems);
        SearchableCombos.install(categoryCombo,
                category -> category == null ? NO_CATEGORY : categoryLabel(category));
        categoryCombo.valueProperty().bindBidirectional(viewModel.categoryProperty());
        viewModel.categoryProperty().addListener(
                (observable, oldValue, newValue) -> refreshFormComboItems());

        SearchableCombos.install(categoryFilterCombo,
                category -> category == null ? ALL_CATEGORIES : categoryLabel(category));
        categoryFilterCombo.valueProperty().bindBidirectional(viewModel.categoryFilterProperty());

        // The supplier filter mirrors the category one: a null value is the
        // first "Todos los proveedores" option and an inactive supplier is
        // still filterable, labelled with its suffix (decision D2).
        SearchableCombos.install(supplierFilterCombo,
                supplier -> supplier == null ? ALL_SUPPLIERS : supplierLabel(supplier));
        supplierFilterCombo.valueProperty().bindBidirectional(viewModel.supplierFilterProperty());
    }

    @Override
    protected void afterLoad() {
        // The combo options come from the database, so the view is loaded first
        // (synchronously) and only then the combos are populated.
        refreshCombos();
    }

    @Override
    protected void decorateFields() {
        decorate(nameField, "name", viewModel.nameErrorProperty());
        decorate(skuField, "sku", viewModel.skuErrorProperty());
        decorate(descriptionField, "description", viewModel.descriptionErrorProperty());
        decorate(priceField, "price", viewModel.priceErrorProperty());
        decorate(minStockField, "minStock", viewModel.minStockErrorProperty());
        decorate(categoryCombo, "category", viewModel.categoryErrorProperty());
    }

    @Override
    protected TableView<Product> table() {
        return productsTable;
    }

    @Override
    protected Button toggleButton() {
        return toggleActiveButton;
    }

    @Override
    protected Label formTitleLabel() {
        return formTitleLabel;
    }

    @Override
    protected Label errorLabel() {
        return errorLabel;
    }

    @Override
    protected Label statusLabel() {
        return statusLabel;
    }

    @Override
    protected String deactivationHeader() {
        return "Dar de baja el producto";
    }

    @Override
    protected String deactivationContent(Product product) {
        return "¿Seguro que quieres dar de baja el producto '" + product.getName()
                + "'? No se elimina de la base de datos y podrás reactivarlo.";
    }

    @FXML
    private void onSave() {
        viewModel.save();
    }

    @FXML
    private void onNewProduct() {
        viewModel.newProduct();
        nameField.requestFocus();
    }

    @FXML
    private void onRefresh() {
        viewModel.load();
        refreshCombos();
    }

    @FXML
    private void onToggleActive() {
        toggleActive();
    }

    @FXML
    private void onClearFilters() {
        viewModel.clearFilters();
        // Both filter combos have to fall back to their "all" option: a null
        // value would leave the button cell empty (see refreshFilterComboItems).
        refreshFilterComboItems();
        refreshSupplierFilterComboItems();
    }

    @FXML
    private void onBack() {
        back();
    }

    /** Rebuilds both combos after a load: their options live in the database. */
    private void refreshCombos() {
        refreshFormComboItems();
        refreshFilterComboItems();
        refreshSupplierFilterComboItems();
    }

    /**
     * Rebuilds the form combo from the active options, appending the currently
     * selected category when it is inactive so the row being edited still
     * displays it. A brand new product therefore never offers it.
     *
     * <p>The first item is a {@code null} rendered as "Sin categoría": the
     * category is optional (RF-03), so picking it is how an existing product is
     * left without one.</p>
     */
    private void refreshFormComboItems() {
        Category current = viewModel.categoryProperty().get();
        // One atomic setAll: clearing the list and refilling it would leave the
        // combo empty for an instant and the selection model may drop the value.
        ObservableList<Category> options = FXCollections.observableArrayList();
        options.add(null);
        options.addAll(viewModel.categoryOptions());
        if (current != null && options.stream()
                .noneMatch(option -> option != null && option.getId() == current.getId())) {
            options.add(current);
        }
        formCategoryItems.setAll(options);
        categoryCombo.setValue(current);
        if (current == null) {
            // With a null value the button cell would render empty and the option
            // would only be discoverable after opening the popup, so the first
            // item ("Sin categoría") is selected explicitly.
            categoryCombo.getSelectionModel().select(0);
        }
    }

    /** Rebuilds the filter list after a reload, keeping the current selection. */
    private void refreshFilterComboItems() {
        Category categoryValue = categoryFilterCombo.getValue();
        categoryFilterCombo.setItems(filterItems(viewModel.categoryFilterOptions()));
        categoryFilterCombo.setValue(categoryValue);
        if (categoryValue == null) {
            // Same reason as in the form combo: "Todas las categorías" is the
            // first item and a null value would leave the button cell empty.
            categoryFilterCombo.getSelectionModel().select(0);
        }
    }

    private static ObservableList<Category> filterItems(ObservableList<Category> options) {
        ObservableList<Category> items = FXCollections.observableArrayList();
        items.add(null);
        items.addAll(options);
        return items;
    }

    /** Rebuilds the supplier filter list after a reload, keeping the current selection. */
    private void refreshSupplierFilterComboItems() {
        Supplier supplierValue = supplierFilterCombo.getValue();
        ObservableList<Supplier> items = FXCollections.observableArrayList();
        items.add(null);
        items.addAll(viewModel.supplierFilterOptions());
        supplierFilterCombo.setItems(items);
        supplierFilterCombo.setValue(supplierValue);
        if (supplierValue == null) {
            // Same reason as in the category combo: "Todos los proveedores" is
            // the first item and a null value would leave the button cell empty.
            supplierFilterCombo.getSelectionModel().select(0);
        }
    }

    /** Marks the categories that were given up without changing the entity label. */
    private static String categoryLabel(Category category) {
        return category.isActive() ? category.getName() : category.getName() + " (dada de baja)";
    }

    /** Marks the suppliers that were given up without changing the entity label. */
    private static String supplierLabel(Supplier supplier) {
        return supplier.isActive() ? supplier.getName() : supplier.getName() + " (dado de baja)";
    }

    private static String categoryName(Product product) {
        return product.getCategory() == null ? "" : product.getCategory().getName();
    }
}
