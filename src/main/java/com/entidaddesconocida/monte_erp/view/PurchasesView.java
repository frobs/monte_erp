package com.entidaddesconocida.monte_erp.view;

import com.entidaddesconocida.monte_erp.app.AppShell;
import com.entidaddesconocida.monte_erp.domain.model.Product;
import com.entidaddesconocida.monte_erp.domain.model.Purchase;
import com.entidaddesconocida.monte_erp.domain.model.PurchaseStatus;
import com.entidaddesconocida.monte_erp.domain.model.Stock;
import com.entidaddesconocida.monte_erp.domain.model.Supplier;
import com.entidaddesconocida.monte_erp.viewmodel.PurchasesViewModel;

import de.saxsys.mvvmfx.FxmlView;
import de.saxsys.mvvmfx.InjectViewModel;

import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Code-behind of the purchase FXML (RF-14 / CU-10).
 *
 * <p>It is not a CRUD screen, so it does not extend {@link CrudView}: it shows the
 * read-only history with the detail of the selected purchase on the left and the
 * new purchase form on the right. Each line becomes a lot, so the detail table
 * shows quantity, purchase price and subtotal; the rules live in
 * {@link PurchasesViewModel}. The <em>Acciones</em> column renders, on every row,
 * only the buttons for the transitions the domain allows for that purchase
 * (receive, return, cancel), and the destructive confirmations stay here, in the
 * view.</p>
 *
 * <p>The history is filtered by date range, supplier and status; the filter bar
 * only binds the controls to the ViewModel, because the criteria travel to the
 * database and the ViewModel re-queries on every change.</p>
 */
public class PurchasesView implements FxmlView<PurchasesViewModel> {

    private static final String ALL_SUPPLIERS = "Todos los proveedores";
    private static final String ALL_STATUSES = "Todos los estados";

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /** Keeps the typed quantity within the {@code int} range (RF-14). */
    private static final int QUANTITY_MAX_DIGITS = 10;

    /** 99999999.99, the same range the product form allows (RF-14). */
    private static final int PRICE_MAX_DIGITS = 8;

    private final AppShell appShell;

    @FXML
    private TableView<Purchase> purchasesTable;

    @FXML
    private DatePicker fromDatePicker;

    @FXML
    private DatePicker toDatePicker;

    @FXML
    private ComboBox<Supplier> supplierFilterCombo;

    @FXML
    private ComboBox<PurchaseStatus> statusFilterCombo;

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
    private TableColumn<Purchase, Void> actionsColumn;

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
        refreshFilterCombos();
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

        // The action column has no value: every row renders the buttons its own
        // status allows, so nothing can be clicked that the domain would reject.
        actionsColumn.setCellFactory(column -> new ActionsCell());

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

        fromDatePicker.valueProperty().bindBidirectional(viewModel.fromDateProperty());
        toDatePicker.valueProperty().bindBidirectional(viewModel.toDateProperty());

        // Both filter combos carry a leading null option that means "todos": a
        // null value would leave the button cell of a searchable combo empty, so
        // that first item is selected explicitly (same as ProductsView).
        supplierFilterCombo.setItems(withAllOption(viewModel.supplierFilterOptions()));
        SearchableCombos.install(supplierFilterCombo,
                supplier -> supplier == null ? ALL_SUPPLIERS : supplier.getName());
        supplierFilterCombo.valueProperty().bindBidirectional(viewModel.supplierFilterProperty());
        selectAllOption(supplierFilterCombo);

        statusFilterCombo.setItems(withAllOption(viewModel.statusFilterOptions()));
        SearchableCombos.install(statusFilterCombo,
                status -> status == null ? ALL_STATUSES : status.label());
        statusFilterCombo.valueProperty().bindBidirectional(viewModel.statusFilterProperty());
        selectAllOption(statusFilterCombo);
    }

    /**
     * Cell of the <em>Acciones</em> column: builds the buttons of the purchase
     * shown on that row. A terminal purchase has no transition, so its cell stays
     * empty.
     */
    private final class ActionsCell extends TableCell<Purchase, Void> {

        @Override
        protected void updateItem(Void item, boolean empty) {
            super.updateItem(item, empty);
            Purchase purchase = purchaseOfThisRow(empty);
            if (purchase == null) {
                setGraphic(null);
                return;
            }
            HBox actions = new HBox(6.0);
            actions.setAlignment(Pos.CENTER_LEFT);
            if (purchase.canTransitionTo(PurchaseStatus.RECEIVED)) {
                actions.getChildren().add(actionButton("Recibir", false,
                        () -> onMarkReceived(purchase)));
            }
            if (purchase.canTransitionTo(PurchaseStatus.RETURNED)) {
                actions.getChildren().add(actionButton("Devolver", true,
                        () -> onReturnPurchase(purchase)));
            }
            if (purchase.canTransitionTo(PurchaseStatus.CANCELLED)) {
                actions.getChildren().add(actionButton("Cancelar", true,
                        () -> onCancelPurchase(purchase)));
            }
            setGraphic(actions.getChildren().isEmpty() ? null : actions);
        }

        private Purchase purchaseOfThisRow(boolean empty) {
            if (empty || getTableView() == null || getTableView().getItems() == null) {
                return null;
            }
            int index = getIndex();
            if (index < 0 || index >= getTableView().getItems().size()) {
                return null;
            }
            return getTableView().getItems().get(index);
        }
    }

    private static Button actionButton(String text, boolean danger, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add("table-action-button");
        if (danger) {
            button.getStyleClass().add("table-action-button-danger");
        }
        button.setOnAction(event -> action.run());
        return button;
    }

    /** Selects the row the button belongs to, then delegates the transition. */
    private void changeStatus(Purchase purchase, PurchaseStatus newStatus) {
        viewModel.selectedPurchaseProperty().set(purchase);
        viewModel.changeStatus(newStatus);
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

    /** Marks the given purchase as received (only offered while pending). */
    private void onMarkReceived(Purchase purchase) {
        changeStatus(purchase, PurchaseStatus.RECEIVED);
    }

    /** Returns the given purchase to the supplier after a destructive confirmation. */
    private void onReturnPurchase(Purchase purchase) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Monte ERP");
        alert.setHeaderText("Devolver la compra #" + purchase.getId());
        alert.setContentText("La compra quedará devuelta al proveedor, su stock dejará de estar"
                + " disponible y no podrá volver a cambiar de estado."
                + " ¿Quieres continuar?");
        alert.showAndWait()
                .filter(button -> button == ButtonType.OK)
                .ifPresent(button -> changeStatus(purchase, PurchaseStatus.RETURNED));
    }

    /** Cancels the given purchase after a destructive confirmation (RNF-05). */
    private void onCancelPurchase(Purchase purchase) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Monte ERP");
        alert.setHeaderText("Cancelar la compra #" + purchase.getId());
        alert.setContentText("La compra quedará cancelada y no podrá volver a cambiar de estado."
                + " ¿Quieres continuar?");
        alert.showAndWait()
                .filter(button -> button == ButtonType.OK)
                .ifPresent(button -> changeStatus(purchase, PurchaseStatus.CANCELLED));
    }

    @FXML
    private void onRefresh() {
        viewModel.load();
        refreshFilterCombos();
    }

    @FXML
    private void onClearFilters() {
        viewModel.clearFilters();
        // With a null value the searchable combos would show an empty button
        // cell, so their "todos" option is selected again.
        selectAllOption(supplierFilterCombo);
        selectAllOption(statusFilterCombo);
    }

    /** Rebuilds the filter combos after a load, keeping the selected values. */
    private void refreshFilterCombos() {
        Supplier supplier = supplierFilterCombo.getValue();
        supplierFilterCombo.setItems(withAllOption(viewModel.supplierFilterOptions()));
        supplierFilterCombo.setValue(supplier);
        selectAllOption(supplierFilterCombo);

        PurchaseStatus status = statusFilterCombo.getValue();
        statusFilterCombo.setItems(withAllOption(viewModel.statusFilterOptions()));
        statusFilterCombo.setValue(status);
        selectAllOption(statusFilterCombo);
    }

    /** Options with a leading {@code null} that stands for "no filter". */
    private static <T> ObservableList<T> withAllOption(List<T> options) {
        ObservableList<T> items = FXCollections.observableArrayList();
        items.add(null);
        items.addAll(options);
        return items;
    }

    private static <T> void selectAllOption(ComboBox<T> combo) {
        if (combo.getValue() == null && !combo.getItems().isEmpty()) {
            combo.getSelectionModel().select(0);
        }
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
