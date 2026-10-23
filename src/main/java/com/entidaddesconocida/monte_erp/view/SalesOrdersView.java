package com.entidaddesconocida.monte_erp.view;

import com.entidaddesconocida.monte_erp.app.AppShell;
import com.entidaddesconocida.monte_erp.domain.model.Customer;
import com.entidaddesconocida.monte_erp.domain.model.Product;
import com.entidaddesconocida.monte_erp.domain.model.SalesOrder;
import com.entidaddesconocida.monte_erp.domain.model.SalesOrderLine;
import com.entidaddesconocida.monte_erp.domain.model.StockIssue;
import com.entidaddesconocida.monte_erp.viewmodel.SalesOrdersViewModel;

import de.saxsys.mvvmfx.FxmlView;
import de.saxsys.mvvmfx.InjectViewModel;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Code-behind of the sales-order FXML (RF-07 / CU-07).
 *
 * <p>It is not a CRUD screen, so it does not extend {@link CrudView}: it shows the
 * read-only history with the detail of the selected order on the left and the new
 * order form on the right. RF-08 adds the contextual status buttons over the
 * selected order; the rules live in {@link SalesOrdersViewModel} and the
 * destructive confirmation (cancel) stays here, in the view.</p>
 */
public class SalesOrdersView implements FxmlView<SalesOrdersViewModel> {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /** Keeps the typed quantity within the {@code int} range (RF-07). */
    private static final int QUANTITY_MAX_DIGITS = 9;

    private final AppShell appShell;

    @FXML
    private TableView<SalesOrder> ordersTable;

    @FXML
    private TableColumn<SalesOrder, String> orderIdColumn;

    @FXML
    private TableColumn<SalesOrder, String> orderDateColumn;

    @FXML
    private TableColumn<SalesOrder, String> customerColumn;

    @FXML
    private TableColumn<SalesOrder, String> statusColumn;

    @FXML
    private TableColumn<SalesOrder, String> totalColumn;

    @FXML
    private TableView<SalesOrderLine> orderLinesTable;

    @FXML
    private TableColumn<SalesOrderLine, String> lineProductColumn;

    @FXML
    private TableColumn<SalesOrderLine, String> lineQuantityColumn;

    @FXML
    private TableColumn<SalesOrderLine, String> lineUnitPriceColumn;

    @FXML
    private TableColumn<SalesOrderLine, String> lineSubtotalColumn;

    @FXML
    private TableView<StockIssue> consumedLotsTable;

    @FXML
    private TableColumn<StockIssue, String> issueProductColumn;

    @FXML
    private TableColumn<StockIssue, String> issueQuantityColumn;

    @FXML
    private TableColumn<StockIssue, String> issueLotColumn;

    @FXML
    private TableColumn<StockIssue, String> issuePurchaseColumn;

    @FXML
    private Button markShippedButton;

    @FXML
    private Button markDeliveredButton;

    @FXML
    private Button cancelOrderButton;

    @FXML
    private Label detailErrorLabel;

    @FXML
    private Label detailStatusLabel;

    @FXML
    private ComboBox<Customer> customerCombo;

    @FXML
    private ComboBox<Product> productCombo;

    @FXML
    private Label stockLabel;

    @FXML
    private TextField quantityField;

    @FXML
    private TableView<SalesOrderLine> draftLinesTable;

    @FXML
    private TableColumn<SalesOrderLine, String> draftProductColumn;

    @FXML
    private TableColumn<SalesOrderLine, String> draftQuantityColumn;

    @FXML
    private TableColumn<SalesOrderLine, String> draftUnitPriceColumn;

    @FXML
    private TableColumn<SalesOrderLine, String> draftSubtotalColumn;

    @FXML
    private Label totalLabel;

    @FXML
    private Label errorLabel;

    @FXML
    private Label statusLabel;

    @InjectViewModel
    private SalesOrdersViewModel viewModel;

    public SalesOrdersView(AppShell appShell) {
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
        orderIdColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().getId() == 0 ? "" : "#" + cell.getValue().getId()));
        orderDateColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().getOrderDate() == null
                        ? "" : DATE_FORMAT.format(cell.getValue().getOrderDate())));
        customerColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().getCustomer() == null ? "" : cell.getValue().getCustomer().getName()));
        statusColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().getStatusLabel()));
        totalColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(formatMoney(cell.getValue().getTotal())));

        lineProductColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                productLabel(cell.getValue().getProduct())));
        lineQuantityColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                String.valueOf(cell.getValue().getQuantity())));
        lineUnitPriceColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(formatMoney(cell.getValue().getUnitPrice())));
        lineSubtotalColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(formatMoney(cell.getValue().subtotal())));

        // RF-15: which lot every unit of the order left from (FIFO traces).
        issueProductColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                productLabel(cell.getValue().getStock() == null
                        ? null : cell.getValue().getStock().getProduct())));
        issueQuantityColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                String.valueOf(cell.getValue().getQuantity())));
        issueLotColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().getStock() == null || cell.getValue().getStock().getEntryDate() == null
                        ? "" : DATE_FORMAT.format(cell.getValue().getStock().getEntryDate())));
        issuePurchaseColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().getStock() == null || cell.getValue().getStock().getPurchase() == null
                        || cell.getValue().getStock().getPurchase().getId() == 0
                        ? "" : "#" + cell.getValue().getStock().getPurchase().getId()));

        draftProductColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                productLabel(cell.getValue().getProduct())));
        draftQuantityColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                String.valueOf(cell.getValue().getQuantity())));
        draftUnitPriceColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(formatMoney(cell.getValue().getUnitPrice())));
        draftSubtotalColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(formatMoney(cell.getValue().subtotal())));

        ordersTable.setItems(viewModel.orders());
        orderLinesTable.setItems(viewModel.selectedOrderLines());
        consumedLotsTable.setItems(viewModel.selectedOrderIssues());
        draftLinesTable.setItems(viewModel.draftLines());

        customerCombo.setItems(viewModel.customers());
        customerCombo.valueProperty().bindBidirectional(viewModel.selectedCustomerProperty());

        productCombo.setItems(viewModel.products());
        productCombo.setCellFactory(list -> productCell());
        productCombo.setButtonCell(productCell());
        productCombo.valueProperty().bindBidirectional(viewModel.selectedProductProperty());

        // RF-09: the available units of the chosen product are shown and become
        // the cap of the quantity field, so an impossible line cannot be typed.
        stockLabel.textProperty().bind(Bindings.createStringBinding(
                this::availableStockText,
                viewModel.selectedProductProperty(), viewModel.availableStockProperty()));
        stockLabel.visibleProperty().bind(viewModel.selectedProductProperty().isNotNull());
        stockLabel.managedProperty().bind(stockLabel.visibleProperty());

        quantityField.textProperty().bindBidirectional(viewModel.quantityProperty());
        // Local input mask: letters, signs and quantities above the stock never
        // reach the ViewModel (RF-07/RF-09).
        quantityField.setTextFormatter(InputMasks.positiveIntegerWithin(QUANTITY_MAX_DIGITS,
                this::quantityUpperBound));

        totalLabel.textProperty().bind(Bindings.createStringBinding(
                () -> "Total: " + formatMoney(viewModel.orderTotalProperty().get()),
                viewModel.orderTotalProperty()));

        bindStatusActions();
    }

    /**
     * RF-08: each status button is shown only while its transition is valid for
     * the selected order; the {@code availableTransitions} list is the dependency
     * of the binding, so it is re-evaluated when the selection changes.
     */
    private void bindStatusActions() {
        markShippedButton.visibleProperty().bind(transitionAvailable(SalesOrder.STATUS_SHIPPED));
        markShippedButton.managedProperty().bind(markShippedButton.visibleProperty());
        markDeliveredButton.visibleProperty().bind(transitionAvailable(SalesOrder.STATUS_DELIVERED));
        markDeliveredButton.managedProperty().bind(markDeliveredButton.visibleProperty());
        cancelOrderButton.visibleProperty().bind(transitionAvailable(SalesOrder.STATUS_CANCELLED));
        cancelOrderButton.managedProperty().bind(cancelOrderButton.visibleProperty());
    }

    private BooleanBinding transitionAvailable(String status) {
        return Bindings.createBooleanBinding(
                () -> viewModel.availableTransitions().contains(status),
                viewModel.availableTransitions());
    }

    /** Stock hint of the chosen product: no text without a selection. */
    private String availableStockText() {
        if (viewModel.selectedProductProperty().get() == null) {
            return "";
        }
        int available = viewModel.availableStockProperty().get();
        return available > 0
                ? "Disponibles: " + available + " unidades" : "Sin stock disponible";
    }

    /**
     * Upper bound of the quantity field: while no product is selected anything
     * can be typed (the ViewModel reports the missing product on save); once one
     * is chosen the bound is its available stock.
     */
    private int quantityUpperBound() {
        return viewModel.selectedProductProperty().get() == null
                ? Integer.MAX_VALUE : viewModel.availableStockProperty().get();
    }

    private void bindSelection() {
        // TableView's selection model is read-only, so it is kept in sync with
        // the ViewModel with listeners in both directions (as in CrudView).
        ordersTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> viewModel.selectedOrderProperty().set(newValue));
        viewModel.selectedOrderProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null) {
                ordersTable.getSelectionModel().clearSelection();
            } else {
                ordersTable.getSelectionModel().select(newValue);
            }
            viewModel.selectOrder(newValue);
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
    private void onAddLine() {
        viewModel.addLine();
    }

    @FXML
    private void onRemoveLine() {
        viewModel.removeLine(draftLinesTable.getSelectionModel().getSelectedItem());
    }

    @FXML
    private void onSave() {
        viewModel.save();
    }

    @FXML
    private void onNewOrder() {
        viewModel.newOrder();
    }

    /** RF-08: marks the selected order as shipped (only offered while pending). */
    @FXML
    private void onMarkShipped() {
        viewModel.changeStatus(SalesOrder.STATUS_SHIPPED);
    }

    /** RF-08: marks the selected order as delivered (only offered while shipped). */
    @FXML
    private void onMarkDelivered() {
        viewModel.changeStatus(SalesOrder.STATUS_DELIVERED);
    }

    /** RF-08: cancels the selected order after a destructive confirmation (RNF-05). */
    @FXML
    private void onCancelOrder() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Monte ERP");
        alert.setHeaderText("Cancelar el pedido");
        alert.setContentText("El pedido quedará cancelado y no podrá volver a cambiar de estado."
                + " ¿Quieres continuar?");
        alert.showAndWait()
                .filter(button -> button == ButtonType.OK)
                .ifPresent(button -> viewModel.changeStatus(SalesOrder.STATUS_CANCELLED));
    }

    @FXML
    private void onRefresh() {
        viewModel.load();
    }

    @FXML
    private void onBack() {
        appShell.showMain();
    }

    /** Product combo cell: the entity {@code toString} is a debug rendering, so the name is shown here. */
    private static ListCell<Product> productCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(Product item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : productLabel(item));
            }
        };
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
