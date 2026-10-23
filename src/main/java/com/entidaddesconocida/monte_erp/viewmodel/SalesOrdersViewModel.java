package com.entidaddesconocida.monte_erp.viewmodel;

import com.entidaddesconocida.monte_erp.domain.model.Customer;
import com.entidaddesconocida.monte_erp.domain.model.Product;
import com.entidaddesconocida.monte_erp.domain.model.SalesOrder;
import com.entidaddesconocida.monte_erp.domain.model.SalesOrderLine;
import com.entidaddesconocida.monte_erp.domain.model.Stock;
import com.entidaddesconocida.monte_erp.domain.model.StockIssue;
import com.entidaddesconocida.monte_erp.domain.model.User;
import com.entidaddesconocida.monte_erp.domain.repository.CustomerRepository;
import com.entidaddesconocida.monte_erp.domain.repository.ProductRepository;
import com.entidaddesconocida.monte_erp.domain.repository.SalesOrderRepository;
import com.entidaddesconocida.monte_erp.domain.repository.StockRepository;
import com.entidaddesconocida.monte_erp.domain.service.FifoAllocator;
import com.entidaddesconocida.monte_erp.domain.service.InsufficientStockException;

import de.saxsys.mvvmfx.ViewModel;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ViewModel of the sales-order screen (RF-07 / CU-07, total from RF-13).
 *
 * <p>It is <strong>not</strong> a CRUD: there is no edition nor deletion, but
 * RF-08 (US-08) adds the status change as the only mutation. It is a
 * master-detail screen that creates an order from several lines and shows the
 * read-only history with the detail of the selected order.</p>
 *
 * <p>The unit price of a line is a snapshot of the product price when the line is
 * added (decision D4) and only one line per product is allowed (D3), rejected
 * with a message. The order is saved as {@code pending} with the signed-in user
 * (D5) and the total is computed from the lines (D6).</p>
 *
 * <p>Stock is checked twice (RF-09/RF-15, decision D4): {@link #addLine()}
 * pre-warns with {@link StockRepository#findAvailable(int)} and does not add an
 * impossible line, while {@link #save()} is the authority (the repository
 * re-reads the lots under lock and may still reject the order). The detail of a
 * selected order also loads the consumed lots ({@link SalesOrderRepository#findIssues(int)}, RF-15).</p>
 *
 * <p>Like the CRUD ViewModels it is free of ValidatorFX: the per-field rules are
 * exposed as one error property per field, so it stays testable with plain JUnit
 * and no JavaFX toolkit.</p>
 */
public class SalesOrdersViewModel implements ViewModel {

    private static final Logger LOGGER = Logger.getLogger(SalesOrdersViewModel.class.getName());

    /** Message shown when the database cannot be reached. */
    protected static final String DATABASE_ERROR =
            "No se pudo conectar con la base de datos. Comprueba que MySQL esté arrancado.";

    /** No customer chosen when saving an order. */
    protected static final String CUSTOMER_REQUIRED = "Selecciona un cliente.";

    /** No product chosen when adding a line. */
    protected static final String PRODUCT_REQUIRED = "Selecciona un producto.";

    /** Quantity is empty, not an integer or not positive. */
    protected static final String QUANTITY_INVALID =
            "La cantidad debe ser un número entero mayor que 0.";

    /** A product can only appear once in the order (decision D3). */
    protected static final String DUPLICATED_PRODUCT = "El producto ya está en el pedido.";

    /** Prefix of the stock pre-warning message (RF-09, decision D4). */
    protected static final String STOCK_INSUFFICIENT = "Stock insuficiente";

    /** An order needs at least one line to be saved. */
    protected static final String LINES_REQUIRED = "Añade al menos una línea al pedido.";

    /** RF-08: no order selected to change its status. */
    protected static final String ORDER_REQUIRED = "Selecciona un pedido.";

    /** RF-08: the transition is not allowed by the domain (origin, target). */
    protected static final String INVALID_TRANSITION =
            "El pedido no puede pasar de \"%s\" a \"%s\".";

    /** RF-08: the optimistic guard did not match; someone else moved the order. */
    protected static final String CONFLICT =
            "El pedido ha cambiado desde que se cargó; se ha actualizado el histórico.";

    private final SalesOrderRepository salesOrderRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final StockRepository stockRepository;
    private final User currentUser;

    private final ObservableList<Customer> customers = FXCollections.observableArrayList();
    private final ObservableList<Product> products = FXCollections.observableArrayList();
    private final ObjectProperty<Customer> selectedCustomer = new SimpleObjectProperty<>();
    private final ObjectProperty<Product> selectedProduct = new SimpleObjectProperty<>();
    private final StringProperty quantity = new SimpleStringProperty("");

    /** Available units of the selected product, refreshed when the selection changes (RF-09). */
    private final ReadOnlyIntegerWrapper availableStock = new ReadOnlyIntegerWrapper(0);

    private final ObservableList<SalesOrderLine> draftLines = FXCollections.observableArrayList();
    private final ReadOnlyObjectWrapper<BigDecimal> orderTotal = new ReadOnlyObjectWrapper<>(BigDecimal.ZERO);

    private final ObservableList<SalesOrder> orders = FXCollections.observableArrayList();
    private final ObjectProperty<SalesOrder> selectedOrder = new SimpleObjectProperty<>();
    private final ObservableList<SalesOrderLine> selectedOrderLines = FXCollections.observableArrayList();

    /** RF-15: lots consumed by the selected order, for the detail table. */
    private final ObservableList<StockIssue> selectedOrderIssues = FXCollections.observableArrayList();

    /** RF-08: statuses reachable from the selected order, in button order. */
    private final ObservableList<String> availableTransitions = FXCollections.observableArrayList();

    private final StringProperty errorMessage = new SimpleStringProperty("");
    private final StringProperty statusMessage = new SimpleStringProperty("");
    private final ReadOnlyStringWrapper customerError = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper productError = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper quantityError = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper stockError = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper linesError = new ReadOnlyStringWrapper("");

    public SalesOrdersViewModel(SalesOrderRepository salesOrderRepository,
                                CustomerRepository customerRepository,
                                ProductRepository productRepository,
                                StockRepository stockRepository,
                                User currentUser) {
        this.salesOrderRepository = salesOrderRepository;
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.stockRepository = stockRepository;
        this.currentUser = currentUser;
        // The view shows the available units of the chosen product and caps the
        // quantity field with them, so the stock is refreshed on every change.
        selectedProduct.addListener((observable, previous, product) -> refreshAvailableStock(product));
    }

    /** Active customers offered in the combo (decision: an inactive one cannot be assigned). */
    public ObservableList<Customer> customers() {
        return customers;
    }

    /** Active products offered in the combo. */
    public ObservableList<Product> products() {
        return products;
    }

    public ObjectProperty<Customer> selectedCustomerProperty() {
        return selectedCustomer;
    }

    public ObjectProperty<Product> selectedProductProperty() {
        return selectedProduct;
    }

    /** Quantity typed by the user; parsed in {@link #addLine()}. */
    public StringProperty quantityProperty() {
        return quantity;
    }

    /**
     * Available units of the product currently selected in the combo (RF-09). It
     * is {@code 0} when there is no selection, and the view uses it both to show
     * the stock and to cap what can be typed in the quantity field.
     */
    public ReadOnlyIntegerProperty availableStockProperty() {
        return availableStock.getReadOnlyProperty();
    }

    /** Lines of the order being drafted. */
    public ObservableList<SalesOrderLine> draftLines() {
        return draftLines;
    }

    /** Total of the draft order, recomputed from the lines (RF-13). */
    public ReadOnlyObjectProperty<BigDecimal> orderTotalProperty() {
        return orderTotal.getReadOnlyProperty();
    }

    /** Read-only history of saved orders, most recent first. */
    public ObservableList<SalesOrder> orders() {
        return orders;
    }

    public ObjectProperty<SalesOrder> selectedOrderProperty() {
        return selectedOrder;
    }

    /** Lines of the selected order, loaded from the repository. */
    public ObservableList<SalesOrderLine> selectedOrderLines() {
        return selectedOrderLines;
    }

    /** RF-15: lots consumed by the selected order, loaded from the repository. */
    public ObservableList<StockIssue> selectedOrderIssues() {
        return selectedOrderIssues;
    }

    /**
     * RF-08: statuses the selected order can move to, in button order. Empty when
     * there is no selection or the order is in a terminal state, so the view can
     * bind the visibility of each action button to it.
     */
    public ObservableList<String> availableTransitions() {
        return availableTransitions;
    }

    public StringProperty errorMessageProperty() {
        return errorMessage;
    }

    public StringProperty statusMessageProperty() {
        return statusMessage;
    }

    public ReadOnlyStringProperty customerErrorProperty() {
        return customerError.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty productErrorProperty() {
        return productError.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty quantityErrorProperty() {
        return quantityError.getReadOnlyProperty();
    }

    /** Stock pre-warning of {@link #addLine()} (RF-09, decision D4). */
    public ReadOnlyStringProperty stockErrorProperty() {
        return stockError.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty linesErrorProperty() {
        return linesError.getReadOnlyProperty();
    }

    /** Loads the combos and the history, and resets the draft order. */
    public void load() {
        errorMessage.set("");
        statusMessage.set("");
        try {
            customers.setAll(customerRepository.findActive());
            products.setAll(productRepository.findAll());
            orders.setAll(salesOrderRepository.findAll());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Could not load the sales orders", e);
            fail(DATABASE_ERROR);
            return;
        }
        resetDraft();
        // The history is reloaded, so a previously selected order is no longer
        // guaranteed to be in the table: drop the selection and its detail.
        selectedOrder.set(null);
        selectedOrderLines.clear();
        selectedOrderIssues.clear();
        availableTransitions.clear();
        clearFieldErrors();
    }

    /**
     * Adds the chosen product to the draft order with the given quantity and the
     * product price snapshot. Validates the product, the quantity and the "one
     * line per product" rule (D3).
     */
    public void addLine() {
        errorMessage.set("");
        statusMessage.set("");
        clearFieldErrors();

        Product product = selectedProduct.get();
        if (product == null) {
            productError.set(PRODUCT_REQUIRED);
        }

        Integer parsedQuantity = parseQuantity();
        if (parsedQuantity == null || parsedQuantity <= 0) {
            quantityError.set(QUANTITY_INVALID);
        }

        if (product != null && containsProduct(product)) {
            productError.set(DUPLICATED_PRODUCT);
        }

        if (hasFieldErrors()) {
            fail(joinedErrors(productError.get(), quantityError.get()));
            return;
        }

        if (!stockCovers(product, parsedQuantity)) {
            return;
        }

        draftLines.add(new SalesOrderLine(parsedQuantity, product.getPrice(), product));
        recomputeTotal();
        clearFieldErrors();
        selectedProduct.set(null);
        quantity.set("");
        succeed("Línea añadida.");
    }

    /** Removes a line from the draft order and recomputes the total. */
    public void removeLine(SalesOrderLine line) {
        if (line != null && draftLines.remove(line)) {
            recomputeTotal();
        }
    }

    /** Clears the draft order so a brand new one can be typed in. */
    public void newOrder() {
        errorMessage.set("");
        statusMessage.set("");
        resetDraft();
        availableTransitions.clear();
        clearFieldErrors();
    }

    /**
     * Saves the draft order: validates the customer and at least one line, then
     * inserts the order as {@code pending} for the signed-in user with the total
     * computed from the lines.
     */
    public void save() {
        clearFieldErrors();
        if (selectedCustomer.get() == null) {
            customerError.set(CUSTOMER_REQUIRED);
        }
        if (draftLines.isEmpty()) {
            linesError.set(LINES_REQUIRED);
        }
        if (hasFieldErrors()) {
            fail(joinedErrors(customerError.get(), linesError.get()));
            return;
        }

        SalesOrder order = new SalesOrder(selectedCustomer.get(), currentUser, draftLines);
        try {
            int id = salesOrderRepository.insert(order);
            orders.setAll(salesOrderRepository.findAll());
            resetDraft();
            clearFieldErrors();
            succeed("Pedido #" + id + " guardado correctamente.");
        } catch (InsufficientStockException e) {
            // The repository re-read the lots under lock and the order no longer
            // fits: keep the draft so the employee can adjust the quantity.
            fail(e.getMessage());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Could not save the sales order", e);
            fail(DATABASE_ERROR);
        }
    }

    /** Loads the lines of the selected order; a {@code null} order clears them. */
    public void selectOrder(SalesOrder order) {
        updateAvailableTransitions(order);
        if (order == null) {
            selectedOrderLines.clear();
            selectedOrderIssues.clear();
            return;
        }
        try {
            selectedOrderLines.setAll(salesOrderRepository.findLines(order.getId()));
            selectedOrderIssues.setAll(salesOrderRepository.findIssues(order.getId()));
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Could not load the order lines", e);
            selectedOrderLines.clear();
            selectedOrderIssues.clear();
            fail(DATABASE_ERROR);
        }
    }

    /**
     * RF-08: moves the selected order to {@code newStatus}. The transition is
     * validated in the domain first, then persisted with the optimistic guard;
     * a 0-row update means someone else moved the order in the meantime, so the
     * history is reloaded and the user is warned instead of overwriting it.
     */
    public void changeStatus(String newStatus) {
        clearFieldErrors();
        SalesOrder order = selectedOrder.get();
        if (order == null) {
            fail(ORDER_REQUIRED);
            return;
        }
        if (!order.canTransitionTo(newStatus)) {
            fail(String.format(INVALID_TRANSITION, SalesOrder.labelFor(order.getStatus()),
                    SalesOrder.labelFor(newStatus)));
            return;
        }
        try {
            int updated = salesOrderRepository.changeStatus(order.getId(), order.getStatus(), newStatus);
            if (updated == 0) {
                orders.setAll(salesOrderRepository.findAll());
                fail(CONFLICT);
                return;
            }
            order.changeStatus(newStatus);
            orders.setAll(salesOrderRepository.findAll());
            SalesOrder reloaded = findById(order.getId());
            selectedOrder.set(reloaded);
            selectOrder(reloaded);
            succeed("Pedido #" + order.getId() + " marcado como "
                    + SalesOrder.labelFor(newStatus) + ".");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Could not change the sales order status", e);
            fail(DATABASE_ERROR);
        }
    }

    private void updateAvailableTransitions(SalesOrder order) {
        if (order == null) {
            availableTransitions.clear();
        } else {
            availableTransitions.setAll(order.allowedTransitions());
        }
    }

    private SalesOrder findById(int id) {
        return orders.stream()
                .filter(order -> order.getId() == id)
                .findFirst()
                .orElse(null);
    }

    private Integer parseQuantity() {
        String value = quantity.get() == null ? "" : quantity.get().trim();
        if (value.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Reloads the available units of the given product (0 for {@code null}) and
     * caps the typed quantity to it. A database failure is reported as a
     * connection error and leaves the stock at 0 so no impossible line is added.
     */
    private void refreshAvailableStock(Product product) {
        if (product == null) {
            availableStock.set(0);
            return;
        }
        int available;
        try {
            available = FifoAllocator.availableTotal(stockRepository.findAvailable(product.getId()));
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Could not load the available stock", e);
            availableStock.set(0);
            fail(DATABASE_ERROR);
            return;
        }
        availableStock.set(available);
        // The field mask stops the user from typing more than the stock, but a
        // product change can leave an already typed quantity above the new stock.
        Integer typed = parseQuantity();
        if (typed != null && typed > available) {
            quantity.set(available > 0 ? String.valueOf(available) : "");
        }
    }

    /**
     * Pre-checks the available lots of the product before adding the line
     * (decision D4): when they do not cover the quantity, warns and leaves the
     * draft untouched. A database failure is reported as a connection error.
     *
     * @return {@code true} when the line can be added
     */
    private boolean stockCovers(Product product, int requested) {
        if (product == null) {
            return false;
        }
        int available;
        try {
            List<Stock> lots = stockRepository.findAvailable(product.getId());
            available = FifoAllocator.availableTotal(lots);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Could not check the available stock", e);
            fail(DATABASE_ERROR);
            return false;
        }
        if (available < requested) {
            String message = InsufficientStockException.formatMessage(product.getName(),
                    requested, available);
            stockError.set(message);
            fail(message);
            return false;
        }
        return true;
    }

    private boolean containsProduct(Product product) {
        return draftLines.stream()
                .anyMatch(line -> line.getProduct() != null
                        && line.getProduct().getId() == product.getId());
    }

    private void recomputeTotal() {
        BigDecimal total = BigDecimal.ZERO;
        for (SalesOrderLine line : draftLines) {
            total = total.add(line.subtotal());
        }
        orderTotal.set(total);
    }

    private void resetDraft() {
        selectedCustomer.set(null);
        selectedProduct.set(null);
        quantity.set("");
        draftLines.clear();
        orderTotal.set(BigDecimal.ZERO);
    }

    private void clearFieldErrors() {
        customerError.set("");
        productError.set("");
        quantityError.set("");
        stockError.set("");
        linesError.set("");
    }

    private boolean hasFieldErrors() {
        return !customerError.get().isBlank() || !productError.get().isBlank()
                || !quantityError.get().isBlank() || !stockError.get().isBlank()
                || !linesError.get().isBlank();
    }

    private void fail(String message) {
        statusMessage.set("");
        errorMessage.set(message);
    }

    private void succeed(String message) {
        errorMessage.set("");
        statusMessage.set(message);
    }

    private static String joinedErrors(String... messages) {
        return List.of(messages).stream()
                .filter(message -> !message.isBlank())
                .reduce((first, second) -> first + "\n" + second)
                .orElse("");
    }
}
