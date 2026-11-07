package com.entidaddesconocida.monte_erp.view;

import com.entidaddesconocida.monte_erp.app.AppShell;
import com.entidaddesconocida.monte_erp.domain.model.Customer;
import com.entidaddesconocida.monte_erp.domain.model.Product;
import com.entidaddesconocida.monte_erp.domain.model.SalesOrder;
import com.entidaddesconocida.monte_erp.domain.model.SalesOrderFilter;
import com.entidaddesconocida.monte_erp.domain.model.SalesOrderLine;
import com.entidaddesconocida.monte_erp.domain.model.StockIssue;
import com.entidaddesconocida.monte_erp.viewmodel.SalesOrdersViewModel;

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
 * Code-behind of the sales-order FXML (RF-07 / CU-07).
 *
 * <p>It is not a CRUD screen, so it does not extend {@link CrudView}: it shows the
 * read-only history with the detail of the selected order on the left and the new
 * order form on the right. RF-08 adds the status change: the <em>Acciones</em>
 * column renders, on every row, only the buttons for the transitions the domain
 * allows for that order (ship, deliver, cancel), and the destructive confirmation
 * (cancel) stays here, in the view.</p>
 *
 * <p>The history is filtered by date range, customer and status; the filter bar
 * only binds the controls to the ViewModel, because the criteria travel to the
 * database and the ViewModel re-queries on every change (same as PurchasesView).</p>
 */
public class SalesOrdersView implements FxmlView<SalesOrdersViewModel> {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final String ALL_CUSTOMERS = "Todos los clientes";
    private static final String ALL_STATUSES = "Todos los estados";

    /** Keeps the typed quantity within the {@code int} range (RF-07). */
    private static final int QUANTITY_MAX_DIGITS = 10;

    private final AppShell appShell;

    @FXML
    private TableView<SalesOrder> ordersTable;

    @FXML
    private DatePicker fromDatePicker;

    @FXML
    private DatePicker toDatePicker;

    @FXML
    private ComboBox<Customer> customerFilterCombo;

    @FXML
    private ComboBox<String> statusFilterCombo;

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
    private TableColumn<SalesOrder, Void> orderActionsColumn;

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

    @FXML
    private Label countLabel;

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
        refreshFilterCombos();
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

        // The action column has no value: every row renders the buttons its own
        // status allows, so nothing can be clicked that the domain would reject.
        orderActionsColumn.setCellFactory(column -> new ActionsCell());

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
        SearchableCombos.install(customerCombo,
                customer -> customer == null ? "" : customer.getName());
        customerCombo.valueProperty().bindBidirectional(viewModel.selectedCustomerProperty());

        productCombo.setItems(viewModel.products());
        SearchableCombos.install(productCombo, SalesOrdersView::productLabel);
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

        countLabel.textProperty().bind(Bindings.size(viewModel.orders()).asString("%d pedidos"));

        fromDatePicker.valueProperty().bindBidirectional(viewModel.fromDateProperty());
        toDatePicker.valueProperty().bindBidirectional(viewModel.toDateProperty());

        // Both filter combos carry a leading null option that means "todos": a
        // null value would leave the button cell of a searchable combo empty, so
        // that first item is selected explicitly (same as PurchasesView).
        customerFilterCombo.setItems(withAllOption(viewModel.customerFilterOptions()));
        SearchableCombos.install(customerFilterCombo,
                customer -> customer == null ? ALL_CUSTOMERS : customer.getName());
        customerFilterCombo.valueProperty().bindBidirectional(viewModel.customerFilterProperty());
        selectAllOption(customerFilterCombo);

        statusFilterCombo.setItems(withAllOption(viewModel.statusFilterOptions()));
        SearchableCombos.install(statusFilterCombo,
                status -> status == null ? ALL_STATUSES : SalesOrder.labelFor(status));
        statusFilterCombo.valueProperty().bindBidirectional(viewModel.statusFilterProperty());
        selectAllOption(statusFilterCombo);
    }

    /**
     * Cell of the <em>Acciones</em> column: builds the buttons of the order shown
     * on that row. A terminal order has no transition, so its cell stays empty.
     */
    private final class ActionsCell extends TableCell<SalesOrder, Void> {

        @Override
        protected void updateItem(Void item, boolean empty) {
            super.updateItem(item, empty);
            SalesOrder order = orderOfThisRow(empty);
            if (order == null) {
                setGraphic(null);
                return;
            }
            HBox actions = new HBox(6.0);
            actions.setAlignment(Pos.CENTER_LEFT);
            if (order.canTransitionTo(SalesOrder.STATUS_SHIPPED)) {
                actions.getChildren().add(actionButton("Marcar enviado", false,
                        () -> changeStatus(order, SalesOrder.STATUS_SHIPPED)));
            }
            if (order.canTransitionTo(SalesOrder.STATUS_DELIVERED)) {
                actions.getChildren().add(actionButton("Marcar entregado", false,
                        () -> changeStatus(order, SalesOrder.STATUS_DELIVERED)));
            }
            if (order.canTransitionTo(SalesOrder.STATUS_CANCELLED)) {
                actions.getChildren().add(actionButton("Cancelar", true,
                        () -> onCancelOrder(order)));
            }
            setGraphic(actions.getChildren().isEmpty() ? null : actions);
        }

        private SalesOrder orderOfThisRow(boolean empty) {
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
    private void changeStatus(SalesOrder order, String newStatus) {
        viewModel.selectedOrderProperty().set(order);
        viewModel.changeStatus(newStatus);
    }

    /** Cancels the given order after a destructive confirmation (RNF-05). */
    private void onCancelOrder(SalesOrder order) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Monte ERP");
        alert.setHeaderText("Cancelar el pedido #" + order.getId());
        alert.setContentText("El pedido quedará cancelado y no podrá volver a cambiar de estado."
                + " ¿Quieres continuar?");
        alert.showAndWait()
                .filter(button -> button == ButtonType.OK)
                .ifPresent(button -> changeStatus(order, SalesOrder.STATUS_CANCELLED));
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
        selectAllOption(customerFilterCombo);
        selectAllOption(statusFilterCombo);
    }

    /** Rebuilds the filter combos after a load, keeping the selected values. */
    private void refreshFilterCombos() {
        Customer customer = customerFilterCombo.getValue();
        customerFilterCombo.setItems(withAllOption(viewModel.customerFilterOptions()));
        customerFilterCombo.setValue(customer);
        selectAllOption(customerFilterCombo);

        String status = statusFilterCombo.getValue();
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
