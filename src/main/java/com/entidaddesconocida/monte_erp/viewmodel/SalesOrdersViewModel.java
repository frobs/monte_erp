package com.entidaddesconocida.monte_erp.viewmodel;

import com.entidaddesconocida.monte_erp.app.BackgroundTasks;
import com.entidaddesconocida.monte_erp.app.SessionUser;
import com.entidaddesconocida.monte_erp.app.SessionValidator;
import com.entidaddesconocida.monte_erp.domain.model.Customer;
import com.entidaddesconocida.monte_erp.domain.model.OrderStatus;
import com.entidaddesconocida.monte_erp.domain.model.Product;
import com.entidaddesconocida.monte_erp.domain.model.SalesOrder;
import com.entidaddesconocida.monte_erp.domain.model.SalesOrderFilter;
import com.entidaddesconocida.monte_erp.domain.model.SalesOrderLine;
import com.entidaddesconocida.monte_erp.domain.model.StockIssue;
import com.entidaddesconocida.monte_erp.domain.repository.CustomerRepository;
import com.entidaddesconocida.monte_erp.domain.repository.ProductRepository;
import com.entidaddesconocida.monte_erp.domain.repository.SalesOrderRepository;
import com.entidaddesconocida.monte_erp.domain.repository.StockRepository;
import com.entidaddesconocida.monte_erp.domain.service.InactiveEntityException;
import com.entidaddesconocida.monte_erp.domain.service.InsufficientStockException;

import de.saxsys.mvvmfx.ViewModel;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
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
 * <p>The responsibilities are delegated (A2): the draft — combos, lines, price
 * snapshot, total and stock pre-warning — lives in {@link SalesOrderDraft}; the
 * history criteria in {@link HistoryFilter}; the RF-08 pipeline in
 * {@link StatusChanger}. This class wires them, runs the insert and reads the
 * detail of the selection.</p>
 *
 * <p>The order is saved as {@code pending} with the signed-in user (D5) and the
 * total is computed from the lines (D6). Stock is checked twice (RF-09/RF-15,
 * decision D4): the draft pre-warns with {@link StockRepository#findAvailable(int)}
 * and does not add an impossible line, while {@link #save()} is the authority (the
 * repository re-reads the lots under lock and may still reject the order). The
 * detail of a selected order also loads the consumed lots
 * ({@link SalesOrderRepository#findIssues(int)}, RF-15).</p>
 *
 * <p>Every query of the screen — the insert, the status change, the detail of the
 * selected order and the stock of the chosen product — runs off the JavaFX thread
 * through {@link BackgroundTasks}; only the state updates come back to it. The
 * detail query is guarded by a generation counter so a slow response for a
 * previous selection can never overwrite the current one.</p>
 *
 * <p>The history can be filtered by date range, customer and status. The criteria
 * travel to the database as a {@link SalesOrderFilter}: every change runs
 * {@code findAll(filter)}, so nothing is filtered in memory. An inverted range is
 * rejected in the domain before any query is issued, and every reload drops the
 * selection so a filtered-out order never keeps its detail on screen.</p>
 *
 * <p>Like the CRUD ViewModels it is free of ValidatorFX: the per-field rules are
 * exposed as one error property per field, so it stays testable with plain JUnit
 * and no JavaFX toolkit.</p>
 */
public class SalesOrdersViewModel implements ViewModel {

    private static final Logger LOGGER = Logger.getLogger(SalesOrdersViewModel.class.getName());

    /** Message shown when the database cannot be reached. */
    protected static final String DATABASE_ERROR = ViewMessages.DATABASE_ERROR;
    private static final String SESSION_EXPIRED = ViewMessages.SESSION_EXPIRED;
    private static final String SAVE_RELOAD_ERROR =
            "Pedido #%d guardado, pero no se pudo actualizar el histórico. Pulsa 'Actualizar'.";

    /** Message shown when the "desde" date is after the "hasta" date. */
    protected static final String PERIOD_ERROR = ViewMessages.PERIOD_ERROR;

    /** Shown when the order was saved but the filter hides the history. */
    private static final String SAVED_WITH_BAD_FILTER =
            "Pedido #%d guardado correctamente. Corrige el filtro para verlo en el histórico.";

    /** No customer chosen when saving an order. */
    protected static final String CUSTOMER_REQUIRED = SalesOrderDraft.CUSTOMER_REQUIRED;

    /** No product chosen when adding a line. */
    protected static final String PRODUCT_REQUIRED = SalesOrderDraft.PRODUCT_REQUIRED;

    /** Quantity is empty, not an integer or not positive. */
    protected static final String QUANTITY_INVALID = SalesOrderDraft.QUANTITY_INVALID;

    /** A product can only appear once in the order (decision D3). */
    protected static final String DUPLICATED_PRODUCT = SalesOrderDraft.DUPLICATED_PRODUCT;

    /** Prefix of the stock pre-warning message (RF-09, decision D4). */
    protected static final String STOCK_INSUFFICIENT = SalesOrderDraft.STOCK_INSUFFICIENT;

    /** An order needs at least one line to be saved. */
    protected static final String LINES_REQUIRED = SalesOrderDraft.LINES_REQUIRED;

    /** RF-08: no order selected to change its status. */
    protected static final String ORDER_REQUIRED = "Selecciona un pedido.";

    /** RF-08: the transition is not allowed by the domain (origin, target). */
    protected static final String INVALID_TRANSITION =
            "El pedido no puede pasar de \"%s\" a \"%s\".";

    /** RF-08: the optimistic guard did not match; someone else moved the order. */
    protected static final String CONFLICT =
            "El pedido ha cambiado desde que se cargó; se ha actualizado el histórico.";

    /** Texts of the RF-08 pipeline, so the in-memory and the stored order agree. */
    private static final StatusChanger.Messages<OrderStatus> STATUS_MESSAGES =
            new StatusChanger.Messages<>(ORDER_REQUIRED, INVALID_TRANSITION, CONFLICT,
                    (id, status) -> "Pedido #" + id + " marcado como " + status.label() + ".");

    private final SalesOrderRepository salesOrderRepository;
    private final CustomerRepository customerRepository;
    private final SessionUser currentUser;
    private final SessionValidator sessionValidator;
    private final BackgroundTasks backgroundTasks;

    private final Feedback feedback = new Feedback();
    private final WriteGuard writeGuard = new WriteGuard();

    private final SalesOrderDraft draft;
    private final HistoryFilter<Customer, OrderStatus> filter;
    private final StatusChanger<SalesOrder, OrderStatus> statusChanger;

    private final ObservableList<SalesOrder> orders = FXCollections.observableArrayList();
    private final ObjectProperty<SalesOrder> selectedOrder = new SimpleObjectProperty<>();
    private final SalesOrderDetail detail;

    public SalesOrdersViewModel(SalesOrderRepository salesOrderRepository,
                                CustomerRepository customerRepository,
                                ProductRepository productRepository,
                                StockRepository stockRepository,
                                SessionUser currentUser) {
        this(salesOrderRepository, customerRepository, productRepository, stockRepository,
                currentUser, null, BackgroundTasks.synchronous());
    }

    public SalesOrdersViewModel(SalesOrderRepository salesOrderRepository,
                                CustomerRepository customerRepository,
                                ProductRepository productRepository,
                                StockRepository stockRepository,
                                SessionUser currentUser,
                                SessionValidator sessionValidator) {
        this(salesOrderRepository, customerRepository, productRepository, stockRepository,
                currentUser, sessionValidator, BackgroundTasks.synchronous());
    }

    /**
     * @param backgroundTasks executor of the insert, the status change, the order
     *                        detail and the stock queries (all of them query the
     *                        database and can wait for another session); {@link
     *                        BackgroundTasks#synchronous()} in tests and a
     *                        virtual-thread executor in the application
     */
    public SalesOrdersViewModel(SalesOrderRepository salesOrderRepository,
                                CustomerRepository customerRepository,
                                ProductRepository productRepository,
                                StockRepository stockRepository,
                                SessionUser currentUser,
                                SessionValidator sessionValidator,
                                BackgroundTasks backgroundTasks) {
        this.salesOrderRepository = salesOrderRepository;
        this.customerRepository = customerRepository;
        this.currentUser = currentUser;
        this.sessionValidator = sessionValidator;
        this.backgroundTasks = backgroundTasks;
        this.draft = new SalesOrderDraft(customerRepository, productRepository, stockRepository,
                backgroundTasks, feedback, this::ensureActiveSession);
        // Every filter change runs one query.
        this.filter = new HistoryFilter<>(OrderStatus.values(), this::reloadHistory);
        this.detail = new SalesOrderDetail(salesOrderRepository, backgroundTasks, feedback);
        this.statusChanger = new StatusChanger<>(backgroundTasks, writeGuard, feedback,
                salesOrderRepository::changeStatus, new StatusChanger.Screen<>() {
                    @Override
                    public boolean reloadHistory() {
                        return SalesOrdersViewModel.this.reloadHistory();
                    }

                    @Override
                    public SalesOrder findById(int id) {
                        return SalesOrdersViewModel.this.findById(id);
                    }

                    @Override
                    public void select(SalesOrder order) {
                        selectedOrder.set(order);
                        selectOrder(order);
                    }
                }, this::onStatusFailure, STATUS_MESSAGES);
    }

    /** Active customers offered in the combo (decision: an inactive one cannot be assigned). */
    public ObservableList<Customer> customers() {
        return draft.customers();
    }

    /** Active products offered in the combo. */
    public ObservableList<Product> products() {
        return draft.products();
    }

    public ObjectProperty<Customer> selectedCustomerProperty() {
        return draft.selectedCustomerProperty();
    }

    public ObjectProperty<Product> selectedProductProperty() {
        return draft.selectedProductProperty();
    }

    /** Quantity typed by the user; parsed by {@link SalesOrderDraft#addLine()}. */
    public StringProperty quantityProperty() {
        return draft.quantityProperty();
    }

    /**
     * Available units of the product currently selected in the combo (RF-09). It
     * is {@code 0} when there is no selection, and the view uses it both to show
     * the stock and to cap what can be typed in the quantity field.
     */
    public ReadOnlyIntegerProperty availableStockProperty() {
        return draft.availableStockProperty();
    }

    /** Lines of the order being drafted. */
    public ObservableList<SalesOrderLine> draftLines() {
        return draft.lines();
    }

    /** Total of the draft order, recomputed from the lines (RF-13). */
    public ReadOnlyObjectProperty<BigDecimal> orderTotalProperty() {
        return draft.totalProperty();
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
        return detail.lines();
    }

    /** RF-15: lots consumed by the selected order, loaded from the repository. */
    public ObservableList<StockIssue> selectedOrderIssues() {
        return detail.issues();
    }

    /**
     * RF-08: statuses the selected order can move to, in button order. Empty when
     * there is no selection or the order is in a terminal state, so the view can
     * bind the visibility of each action button to it.
     */
    public ObservableList<OrderStatus> availableTransitions() {
        return statusChanger.availableTransitions();
    }

    /** Lower bound of the history filter; {@code null} means "unbounded". */
    public ObjectProperty<LocalDate> fromDateProperty() {
        return filter.fromDateProperty();
    }

    /** Upper bound of the history filter; {@code null} means "unbounded". */
    public ObjectProperty<LocalDate> toDateProperty() {
        return filter.toDateProperty();
    }

    /** Customer filter; {@code null} means "every customer". */
    public ObjectProperty<Customer> customerFilterProperty() {
        return filter.entityProperty();
    }

    /** Status filter; {@code null} means "every status". */
    public ObjectProperty<OrderStatus> statusFilterProperty() {
        return filter.statusProperty();
    }

    /**
     * Customers offered by the filter: every one, active or not, so an old order
     * of a customer given up can still be listed (like RF-11 and the purchase
     * filter).
     */
    public ObservableList<Customer> customerFilterOptions() {
        return filter.entityOptions();
    }

    /** Statuses offered by the filter, in domain order. */
    public ObservableList<OrderStatus> statusFilterOptions() {
        return filter.statusOptions();
    }

    public StringProperty errorMessageProperty() {
        return feedback.errorMessageProperty();
    }

    public StringProperty statusMessageProperty() {
        return feedback.statusMessageProperty();
    }

    public ReadOnlyStringProperty customerErrorProperty() {
        return draft.customerErrorProperty();
    }

    public ReadOnlyStringProperty productErrorProperty() {
        return draft.productErrorProperty();
    }

    public ReadOnlyStringProperty quantityErrorProperty() {
        return draft.quantityErrorProperty();
    }

    /** Stock pre-warning of {@link #addLine()} (RF-09, decision D4). */
    public ReadOnlyStringProperty stockErrorProperty() {
        return draft.stockErrorProperty();
    }

    public ReadOnlyStringProperty linesErrorProperty() {
        return draft.linesErrorProperty();
    }

    /** Loads the combos, the filter options and the filtered history. */
    public void load() {
        feedback.clear();
        if (!ensureActiveSession()) {
            return;
        }
        try {
            draft.loadCombos();
            // The filter lists every customer: an inactive one still bought from us.
            customerFilterOptions().setAll(customerRepository.findAll());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Could not load the sales orders", e);
            feedback.fail(DATABASE_ERROR);
            return;
        }
        draft.reset();
        draft.clearFieldErrors();
        reloadHistory();
    }

    /** Drops every filter and re-queries the whole history. */
    public void clearFilters() {
        filter.reset();
        reloadHistory();
    }

    /**
     * Runs the query of the current filter and drops the selection, because a
     * filtered-out order must not stay selected with its detail on screen.
     * Returns {@code false} when the history could not be refreshed: the error
     * message is already set and the caller decides how to report its write.
     */
    private boolean reloadHistory() {
        SalesOrderFilter current;
        try {
            current = currentFilter();
        } catch (IllegalArgumentException e) {
            orders.clear();
            clearSelection();
            feedback.fail(PERIOD_ERROR);
            return false;
        }
        // A corrected range must clear the message that rejected the old one.
        if (PERIOD_ERROR.equals(feedback.error())) {
            feedback.clearError();
        }
        try {
            orders.setAll(salesOrderRepository.findAll(current));
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Could not load the sales orders", e);
            orders.clear();
            clearSelection();
            feedback.fail(DATABASE_ERROR);
            return false;
        }
        clearSelection();
        return true;
    }

    private SalesOrderFilter currentFilter() {
        Customer customer = filter.entityProperty().get();
        return SalesOrderFilter.of(filter.fromDateProperty().get(), filter.toDateProperty().get(),
                customer == null ? null : customer.getId(), filter.statusProperty().get());
    }

    private void clearSelection() {
        selectedOrder.set(null);
        // Any detail query in flight belongs to the previous selection.
        detail.clear();
        statusChanger.availableTransitions().clear();
    }

    /** Adds the chosen product to the draft order (see {@link SalesOrderDraft#addLine()}). */
    public void addLine() {
        if (ensureActiveSession()) {
            draft.addLine();
        }
    }

    /** Removes a line from the draft order and recomputes the total. */
    public void removeLine(SalesOrderLine line) {
        draft.removeLine(line);
    }

    /** Clears the draft order so a brand new one can be typed in. */
    public void newOrder() {
        feedback.clear();
        draft.reset();
        statusChanger.availableTransitions().clear();
        draft.clearFieldErrors();
    }

    /**
     * Saves the draft order: validates the customer and at least one line, then
     * inserts the order as {@code pending} for the signed-in user with the total
     * computed from the lines.
     */
    public void save() {
        if (!ensureActiveSession()) {
            return;
        }
        String invalid = draft.validateForSave();
        if (!invalid.isEmpty()) {
            feedback.fail(invalid);
            return;
        }
        if (!draft.totalWithinLimit()) {
            feedback.fail(SalesOrderDraft.TOTAL_LIMIT_ERROR);
            return;
        }

        if (!writeGuard.tryBegin()) {
            return;
        }
        SalesOrder order = draft.buildOrder(currentUser.toEntityReference());
        // The insert locks the customer and the stock lots (FOR UPDATE), so it can
        // wait for another session: it must not run on the UI thread.
        backgroundTasks.submit(
                () -> salesOrderRepository.insert(order),
                id -> {
                    writeGuard.end();
                    onOrderSaved(id);
                },
                failure -> {
                    writeGuard.end();
                    onOrderSaveFailure(failure);
                });
    }

    /**
     * Applies a committed order: the draft is discarded before the independent
     * history read, so a reload failure cannot cause a duplicate.
     */
    private void onOrderSaved(int id) {
        draft.reset();
        draft.clearFieldErrors();
        if (reloadHistory()) {
            feedback.succeed("Pedido #" + id + " guardado correctamente.");
        } else if (PERIOD_ERROR.equals(feedback.error())) {
            // The write succeeded; only the filter hides the history.
            feedback.status(String.format(SAVED_WITH_BAD_FILTER, id));
        } else {
            LOGGER.log(Level.WARNING, "Sales order was saved but its history could not be reloaded");
            feedback.fail(String.format(SAVE_RELOAD_ERROR, id));
        }
    }

    /**
     * Reports a rejected insert. The domain exceptions carry the message the
     * employee must read (inactive customer, not enough stock); anything else is
     * a connection failure. The draft is kept so it can be corrected.
     */
    private void onOrderSaveFailure(Throwable failure) {
        if (failure instanceof InactiveEntityException
                || failure instanceof InsufficientStockException) {
            feedback.fail(failure.getMessage());
            return;
        }
        LOGGER.log(Level.SEVERE, "Could not save the sales order", failure);
        feedback.fail(DATABASE_ERROR);
    }

    /**
     * Loads the lines and the consumed lots of the selected order; a {@code null}
     * order clears them. The two queries run in the background and only the
     * newest submission is applied, so a slow answer for a previous order never
     * paints its detail over the current one.
     */
    public void selectOrder(SalesOrder order) {
        if (!ensureActiveSession()) {
            return;
        }
        statusChanger.updateFor(order);
        detail.load(order);
    }

    /**
     * RF-08: moves the selected order to {@code newStatus} through the shared
     * pipeline (domain validation, optimistic guard and conflict reload).
     */
    public void changeStatus(OrderStatus newStatus) {
        if (!ensureActiveSession()) {
            return;
        }
        draft.clearFieldErrors();
        statusChanger.change(selectedOrder.get(), newStatus);
    }

    /** Anything but the domain rejections already mapped by the pipeline. */
    private void onStatusFailure(Throwable failure) {
        LOGGER.log(Level.SEVERE, "Could not change the sales order status", failure);
        feedback.fail(DATABASE_ERROR);
    }

    private boolean ensureActiveSession() {
        if (sessionValidator == null) {
            return true;
        }
        try {
            if (sessionValidator.refresh()) {
                return true;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Could not revalidate the signed-in user", e);
            feedback.fail(DATABASE_ERROR);
            return false;
        }
        orders.clear();
        clearSelection();
        draft.reset();
        feedback.fail(SESSION_EXPIRED);
        return false;
    }

    private SalesOrder findById(int id) {
        return orders.stream()
                .filter(order -> order.getId() == id)
                .findFirst()
                .orElse(null);
    }
}
