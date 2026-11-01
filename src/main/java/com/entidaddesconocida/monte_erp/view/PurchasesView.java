package com.entidaddesconocida.monte_erp.view;

import com.entidaddesconocida.monte_erp.app.AppShell;
import com.entidaddesconocida.monte_erp.domain.model.Product;
import com.entidaddesconocida.monte_erp.domain.model.Purchase;
import com.entidaddesconocida.monte_erp.domain.model.Stock;
import com.entidaddesconocida.monte_erp.domain.model.Supplier;
import com.entidaddesconocida.monte_erp.viewmodel.PurchasesViewModel;

import de.saxsys.mvvmfx.FxmlView;
import de.saxsys.mvvmfx.InjectViewModel;

import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Code-behind of the purchase FXML (RF-14 / CU-10).
 *
 * <p>It is not a CRUD screen, so it does not extend {@link CrudView}: it shows the
 * read-only history with the detail of the selected purchase on the left and the
 * new purchase form on the right. Each line becomes a lot, so the detail table
 * shows quantity, purchase price and subtotal; the rules live in
 * {@link PurchasesViewModel}.</p>
 */
public class PurchasesView implements FxmlView<PurchasesViewModel> {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /** Keeps the typed quantity within the {@code int} range (RF-14). */
    private static final int QUANTITY_MAX_DIGITS = 9;

    /** 99999999.99, the same range the product form allows (RF-14). */
    private static final int PRICE_MAX_DIGITS = 8;

    private final AppShell appShell;

    @FXML
    private TableView<Purchase> purchasesTable;

    @FXML
    private TableColumn<Purchase, String> purchaseIdColumn;

    @FXML
    private TableColumn<Purchase, String> purchaseDateColumn;

    @FXML
    private TableColumn<Purchase, String> supplierColumn;

    @FXML
    private TableColumn<Purchase, String> statusColumn;

    @FXML
    private TableColumn<Purchase, String> totalColumn;

    @FXML
    private TableView<Stock> purchaseLotsTable;

    @FXML
    private TableColumn<Stock, String> lotProductColumn;

    @FXML
    private TableColumn<Stock, String> lotQuantityColumn;

    @FXML
    private TableColumn<Stock, String> lotPurchasePriceColumn;

    @FXML
    private TableColumn<Stock, String> lotSubtotalColumn;

    @FXML
    private Label detailErrorLabel;

    @FXML
    private Label detailStatusLabel;

    @FXML
    private ComboBox<Supplier> supplierCombo;

    @FXML
    private ComboBox<Product> productCombo;

    @FXML
    private TextField quantityField;

    @FXML
    private TextField purchasePriceField;

    @FXML
    private TableView<Stock> draftLotsTable;

    @FXML
    private TableColumn<Stock, String> draftProductColumn;

    @FXML
    private TableColumn<Stock, String> draftQuantityColumn;

    @FXML
    private TableColumn<Stock, String> draftPurchasePriceColumn;

    @FXML
    private TableColumn<Stock, String> draftSubtotalColumn;

    @FXML
    private Label totalLabel;

    @FXML
    private Label errorLabel;

    @FXML
    private Label statusLabel;

    @FXML
    private Label countLabel;

    @InjectViewModel
    private PurchasesViewModel viewModel;

    public PurchasesView(AppShell appShell) {
        this.appShell = appShell;
    }

    @FXML
    private void initialize() {
        setupFields();
        bindSelection();
        bindMessages();
        viewModel.load();
    }

    private void setupFields() {
        purchaseIdColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().getId() == 0 ? "" : "#" + cell.getValue().getId()));
        purchaseDateColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().getPurchaseDate() == null
                        ? "" : DATE_FORMAT.format(cell.getValue().getPurchaseDate())));
        supplierColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().getSupplier() == null ? "" : cell.getValue().getSupplier().getName()));
        statusColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().getStatusLabel()));
        totalColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(formatMoney(cell.getValue().getTotal())));

        lotProductColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                productLabel(cell.getValue().getProduct())));
        lotQuantityColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                String.valueOf(cell.getValue().getInitialQuantity())));
        lotPurchasePriceColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(formatMoney(cell.getValue().getPurchasePrice())));
        lotSubtotalColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(formatMoney(cell.getValue().subtotal())));

        draftProductColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                productLabel(cell.getValue().getProduct())));
        draftQuantityColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                String.valueOf(cell.getValue().getInitialQuantity())));
        draftPurchasePriceColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(formatMoney(cell.getValue().getPurchasePrice())));
        draftSubtotalColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(formatMoney(cell.getValue().subtotal())));

        purchasesTable.setItems(viewModel.purchases());
        purchaseLotsTable.setItems(viewModel.selectedPurchaseLots());
        draftLotsTable.setItems(viewModel.draftLots());

        supplierCombo.setItems(viewModel.suppliers());
        SearchableCombos.install(supplierCombo,
                supplier -> supplier == null ? "" : supplier.getName());
        supplierCombo.valueProperty().bindBidirectional(viewModel.selectedSupplierProperty());

        productCombo.setItems(viewModel.products());
        SearchableCombos.install(productCombo, PurchasesView::productLabel);
        productCombo.valueProperty().bindBidirectional(viewModel.selectedProductProperty());

        quantityField.textProperty().bindBidirectional(viewModel.quantityProperty());
        purchasePriceField.textProperty().bindBidirectional(viewModel.purchasePriceProperty());
        // Local input masks: letters and signs never reach the ViewModel (RF-14).
        quantityField.setTextFormatter(InputMasks.positiveInteger(QUANTITY_MAX_DIGITS));
        purchasePriceField.setTextFormatter(InputMasks.money(PRICE_MAX_DIGITS));

        totalLabel.textProperty().bind(Bindings.createStringBinding(
                () -> "Total: " + formatMoney(viewModel.purchaseTotalProperty().get()),
                viewModel.purchaseTotalProperty()));

        countLabel.textProperty().bind(Bindings.size(viewModel.purchases()).asString("%d compras"));
    }

    private void bindSelection() {
        // TableView's selection model is read-only, so it is kept in sync with
        // the ViewModel with listeners in both directions (as in CrudView).
        purchasesTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> viewModel.selectedPurchaseProperty().set(newValue));
        viewModel.selectedPurchaseProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null) {
                purchasesTable.getSelectionModel().clearSelection();
            } else {
                purchasesTable.getSelectionModel().select(newValue);
            }
            viewModel.selectPurchase(newValue);
        });
    }

    private void bindMessages() {
        errorLabel.textProperty().bind(viewModel.errorMessageProperty());
        errorLabel.visibleProperty().bind(viewModel.errorMessageProperty().isNotEmpty());
        errorLabel.managedProperty().bind(viewModel.errorMessageProperty().isNotEmpty());

        statusLabel.textProperty().bind(viewModel.statusMessageProperty());
        statusLabel.visibleProperty().bind(viewModel.statusMessageProperty().isNotEmpty());
        statusLabel.managedProperty().bind(viewModel.statusMessageProperty().isNotEmpty());

        detailErrorLabel.textProperty().bind(viewModel.errorMessageProperty());
        detailErrorLabel.visibleProperty().bind(viewModel.errorMessageProperty().isNotEmpty());
        detailErrorLabel.managedProperty().bind(viewModel.errorMessageProperty().isNotEmpty());

        detailStatusLabel.textProperty().bind(viewModel.statusMessageProperty());
        detailStatusLabel.visibleProperty().bind(viewModel.statusMessageProperty().isNotEmpty());
        detailStatusLabel.managedProperty().bind(viewModel.statusMessageProperty().isNotEmpty());
    }

    @FXML
    private void onAddLot() {
        viewModel.addLot();
    }

    @FXML
    private void onRemoveLot() {
        viewModel.removeLot(draftLotsTable.getSelectionModel().getSelectedItem());
    }

    @FXML
    private void onSave() {
        viewModel.save();
    }

    @FXML
    private void onNewPurchase() {
        viewModel.newPurchase();
    }

    @FXML
    private void onRefresh() {
        viewModel.load();
    }

    @FXML
    private void onBack() {
        appShell.showMain();
    }

    private static String productLabel(Product product) {
        if (product == null) {
            return "";
        }
        return product.getSku() == null || product.getSku().isBlank()
                ? product.getName() : product.getName() + " (" + product.getSku() + ")";
    }

    private static String formatMoney(BigDecimal amount) {
        return amount == null ? "" : String.format(Locale.ROOT, "%.2f €",
                amount.setScale(2, RoundingMode.HALF_UP));
    }
}
