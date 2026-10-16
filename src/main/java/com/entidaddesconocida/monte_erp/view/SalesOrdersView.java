package com.entidaddesconocida.monte_erp.view;

import com.entidaddesconocida.monte_erp.app.AppShell;
import com.entidaddesconocida.monte_erp.domain.model.Customer;
import com.entidaddesconocida.monte_erp.domain.model.Product;
import com.entidaddesconocida.monte_erp.domain.model.SalesOrder;
import com.entidaddesconocida.monte_erp.domain.model.SalesOrderLine;
import com.entidaddesconocida.monte_erp.viewmodel.SalesOrdersViewModel;

import de.saxsys.mvvmfx.FxmlView;
import de.saxsys.mvvmfx.InjectViewModel;

import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
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
 * order form on the right. Navigation is delegated to the {@link AppShell}; the
 * rules live in {@link SalesOrdersViewModel}.</p>
 */
public class SalesOrdersView implements FxmlView<SalesOrdersViewModel> {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

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
    private ComboBox<Customer> customerCombo;

    @FXML
    private ComboBox<Product> productCombo;

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
        draftLinesTable.setItems(viewModel.draftLines());

        customerCombo.setItems(viewModel.customers());
        customerCombo.valueProperty().bindBidirectional(viewModel.selectedCustomerProperty());

        productCombo.setItems(viewModel.products());
        productCombo.setCellFactory(list -> productCell());
        productCombo.setButtonCell(productCell());
        productCombo.valueProperty().bindBidirectional(viewModel.selectedProductProperty());

        quantityField.textProperty().bindBidirectional(viewModel.quantityProperty());

        totalLabel.textProperty().bind(Bindings.createStringBinding(
                () -> "Total: " + formatMoney(viewModel.orderTotalProperty().get()),
                viewModel.orderTotalProperty()));
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
