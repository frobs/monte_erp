package com.entidaddesconocida.monte_erp.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Customer sales order (the {@code SALES_ORDER} table of the E-R diagram),
 * introduced by RF-07 / CU-07.
 *
 * <p>The order is created with the {@link #STATUS_PENDING} state and with the
 * {@code user_id} of the signed-in employee (decision D5); the total is derived
 * from the lines ({@link #calculateTotal()}, RF-13) and must never be typed by
 * the user. {@code order_date} is filled by the database default, so the history
 * reloads from the database instead of trusting the in-memory date.</p>
 *
 * <p>The status is not a free string: RF-08 (US-08) turns the pipeline into a
 * domain invariant through {@link #allowedTransitions()},
 * {@link #canTransitionTo(String)} and {@link #changeStatus(String)}. The only
 * way to move an order is {@code changeStatus(...)}, which rejects retrogressions
 * ({@code shipped → pending}), leaving a terminal state
 * ({@code delivered}/{@code cancelled}), repeating the current state and any
 * unknown/null target. {@code setStatus(String)} is deliberately not public: the
 * database rehydration goes through the full constructor with the trusted ENUM
 * value.</p>
 */
public class SalesOrder {

    /** State of a freshly created order, matching the {@code SALES_ORDER.status} ENUM. */
    public static final String STATUS_PENDING = "pending";

    /** State of an order already shipped. */
    public static final String STATUS_SHIPPED = "shipped";

    /** State of an order delivered to the customer. */
    public static final String STATUS_DELIVERED = "delivered";

    /** State of a cancelled order. */
    public static final String STATUS_CANCELLED = "cancelled";

    /** Spanish labels of the four ENUM values, shared with the user interface (RF-07). */
    private static final Map<String, String> STATUS_LABELS = Map.of(
            STATUS_PENDING, "Pendiente",
            STATUS_SHIPPED, "Enviado",
            STATUS_DELIVERED, "Entregado",
            STATUS_CANCELLED, "Cancelado");

    private int id;
    private LocalDateTime orderDate;
    private String status;
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
        this.status = STATUS_PENDING;
        this.orderDate = LocalDateTime.now();
        this.total = calculateTotal();
    }

    /** Full constructor, used when mapping a {@code SALES_ORDER} row. */
    public SalesOrder(int id, LocalDateTime orderDate, String status, BigDecimal total,
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

    public String getStatus() {
        return status;
    }

    /**
     * Transitions allowed from the current status, in button order (RF-08):
     * {@code pending → shipped/cancelled}, {@code shipped → delivered/cancelled}.
     * Empty for a terminal status ({@code delivered}/{@code cancelled}) or an
     * unknown/absent one. The returned list is immutable, so the rule cannot be
     * mutated from outside.
     */
    public List<String> allowedTransitions() {
        if (STATUS_PENDING.equals(status)) {
            return List.of(STATUS_SHIPPED, STATUS_CANCELLED);
        }
        if (STATUS_SHIPPED.equals(status)) {
            return List.of(STATUS_DELIVERED, STATUS_CANCELLED);
        }
        return List.of();
    }

    /**
     * Whether the order can move to {@code target} from its current status.
     * Repeatedly asking for the current status, a regression, leaving a terminal
     * status, an unknown target or {@code null} are all rejected.
     */
    public boolean canTransitionTo(String target) {
        return target != null && allowedTransitions().contains(target);
    }

    /** Whether the status is final: no transition leaves {@code delivered}/{@code cancelled}. */
    public boolean isTerminal() {
        return STATUS_DELIVERED.equals(status) || STATUS_CANCELLED.equals(status);
    }

    /**
     * Single gate for status changes (RF-08): validates the transition against
     * {@link #allowedTransitions()} and moves the order, or throws
     * {@link IllegalStateException} naming the origin and the rejected target.
     */
    public void changeStatus(String target) {
        if (!canTransitionTo(target)) {
            throw new IllegalStateException("Transición de estado no permitida: \""
                    + status + "\" → \"" + target + "\".");
        }
        this.status = target;
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

    /** Lines of the order; may be empty while the order is being drafted. */
    public List<SalesOrderLine> getLines() {
        if (lines == null) {
            lines = new ArrayList<>();
        }
        return lines;
    }

    public void setLines(List<SalesOrderLine> lines) {
        this.lines = lines == null ? new ArrayList<>() : new ArrayList<>(lines);
    }

    /**
     * Total of the order: the sum of the line subtotals (RF-13). Zero when the
     * order has no lines.
     */
    public BigDecimal calculateTotal() {
        BigDecimal result = BigDecimal.ZERO;
        for (SalesOrderLine line : getLines()) {
            result = result.add(line.subtotal());
        }
        return result;
    }

    /** Human readable status label, for the user interface (Spanish). */
    public String getStatusLabel() {
        return labelFor(status);
    }

    /**
     * Human readable label of a status value (RF-07/RF-08), shared with the
     * messages of the ViewModel. An unknown/absent value is rendered raw so a
     * diagnostic message never lies about the state.
     *
     * <p>The {@code null} check is needed because the immutable map rejects a
     * {@code null} key: without it, {@code labelFor(null)} would throw instead of
     * returning the raw value.</p>
     */
    public static String labelFor(String status) {
        if (status == null) {
            return String.valueOf(status);
        }
        return STATUS_LABELS.getOrDefault(status, status);
    }

    @Override
    public String toString() {
        String customerName = customer == null ? "?" : customer.getName();
        return "SalesOrder{id=" + id + ", status='" + status + "', total=" + total
                + ", customer=" + customerName + '}';
    }
}
