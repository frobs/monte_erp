package com.entidaddesconocida.monte_erp.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Customer sales order (the {@code SALES_ORDER} table of the E-R diagram),
 * introduced by RF-07 / CU-07.
 *
 * <p>The order is created with the {@link OrderStatus#PENDING} state and with the
 * {@code user_id} of the signed-in employee (decision D5); the total is derived
 * from the lines ({@link #calculateTotal()}, RF-13) and must never be typed by
 * the user. {@code order_date} is filled by the database default, so the history
 * reloads from the database instead of trusting the in-memory date.</p>
 *
 * <p>The status is an {@link OrderStatus}, not a free string: RF-08 (US-08) makes
 * the pipeline a domain invariant through {@link OrderStatus#transitions()}, so an
 * unknown state cannot even be constructed. The only way to move an order is
 * {@link #changeStatus(OrderStatus)}, which rejects retrogressions
 * ({@code shipped → pending}), leaving a terminal state
 * ({@code delivered}/{@code cancelled}), repeating the current state and a
 * {@code null} target. There is deliberately no {@code setStatus}: the database
 * rehydration goes through the full constructor with the value parsed by
 * {@link OrderStatus#fromDb(String)}.</p>
 */
public class SalesOrder implements StatusWorkflow<OrderStatus> {

    private int id;
    private LocalDateTime orderDate;
    private OrderStatus status;
    private BigDecimal total;
    private Customer customer;
    private User user;
    private List<SalesOrderLine> lines;

    public SalesOrder() {
        this.lines = new ArrayList<>();
        this.total = BigDecimal.ZERO;
    }

    /**
     * Builds a brand new order: {@code pending}, created now, with the total
     * computed from the given lines.
     */
    public SalesOrder(Customer customer, User user, List<SalesOrderLine> lines) {
        this.customer = customer;
        this.user = user;
        this.lines = lines == null ? new ArrayList<>() : new ArrayList<>(lines);
        this.status = OrderStatus.PENDING;
        this.orderDate = LocalDateTime.now();
        this.total = calculateTotal();
    }

    /** Full constructor, used when mapping a {@code SALES_ORDER} row. */
    public SalesOrder(int id, LocalDateTime orderDate, OrderStatus status, BigDecimal total,
                      Customer customer, User user, List<SalesOrderLine> lines) {
        this.id = id;
        this.orderDate = orderDate;
        this.status = status;
        this.total = total;
        this.customer = customer;
        this.user = user;
        this.lines = lines == null ? new ArrayList<>() : new ArrayList<>(lines);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public LocalDateTime getOrderDate() {
        return orderDate;
    }

    public void setOrderDate(LocalDateTime orderDate) {
        this.orderDate = orderDate;
    }

    public OrderStatus getStatus() {
        return status;
    }

    /**
     * Transitions allowed from the current status, in button order (RF-08). The
     * rule lives in {@link OrderStatus#transitions()}, so it cannot drift from the
     * state machine.
     */
    public List<OrderStatus> allowedTransitions() {
        return status == null ? List.of() : status.transitions();
    }

    /**
     * Whether the order can move to {@code target} from its current status.
     * Repeating the current status, a regression, leaving a terminal status, an
     * unknown target or {@code null} are all rejected.
     */
    public boolean canTransitionTo(OrderStatus target) {
        return target != null && allowedTransitions().contains(target);
    }

    /** Whether the status is final: no transition leaves {@code delivered}/{@code cancelled}. */
    public boolean isTerminal() {
        return status != null && status.isTerminal();
    }

    /**
     * Single gate for status changes (RF-08): validates the transition against
     * {@link #allowedTransitions()} and moves the order, or throws
     * {@link IllegalStateException} naming the origin and the rejected target.
     */
    public void changeStatus(OrderStatus target) {
        if (!canTransitionTo(target)) {
            throw new IllegalStateException("Transición de estado no permitida: \""
                    + dbValueOf(status) + "\" → \"" + dbValueOf(target) + "\".");
        }
        this.status = target;
    }

    /** Database value of a status, or {@code null} as text when there is none. */
    private static String dbValueOf(OrderStatus status) {
        return status == null ? "null" : status.dbValue();
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    /** Customer the order is billed to (the {@code customer_id} foreign key). */
    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    /** Employee that created the order (the {@code user_id} foreign key). */
    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    /**
     * Lines of the order; may be empty while the order is being drafted. The
     * constructors and {@link #setLines(List)} guarantee a non-null list, so no
     * defensive initialization is needed here.
     *
     * <p>The returned list is a read-only view of the internal one: a caller
     * cannot add or remove lines behind the back of the aggregate. Mutations go
     * through {@link #setLines(List)}, which copies.</p>
     *
     * @throws UnsupportedOperationException on any attempt to modify the view
     */
    public List<SalesOrderLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    public void setLines(List<SalesOrderLine> lines) {
        this.lines = lines == null ? new ArrayList<>() : new ArrayList<>(lines);
    }

    /**
     * Total of the order: the sum of the line subtotals (RF-13). Zero when the
     * order has no lines.
     */
    public BigDecimal calculateTotal() {
        return getLines().stream()
                .map(SalesOrderLine::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Human readable status label, for the user interface (Spanish). */
    public String getStatusLabel() {
        return status == null ? "" : status.label();
    }

    @Override
    public String toString() {
        String customerName = customer == null ? "?" : customer.getName();
        return "SalesOrder{id=" + id + ", status='" + status + "', total=" + total
                + ", customer=" + customerName + '}';
    }
}
