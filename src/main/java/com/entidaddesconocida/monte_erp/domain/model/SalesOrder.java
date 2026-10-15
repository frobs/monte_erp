package com.entidaddesconocida.monte_erp.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
 * <p>{@code changeStatus(...)} belongs to RF-08 (US-08) and is deliberately not
 * included yet.</p>
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

    public void setStatus(String status) {
        this.status = status;
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
        if (STATUS_SHIPPED.equals(status)) {
            return "Enviado";
        }
        if (STATUS_DELIVERED.equals(status)) {
            return "Entregado";
        }
        if (STATUS_CANCELLED.equals(status)) {
            return "Cancelado";
        }
        return "Pendiente";
    }

    @Override
    public String toString() {
        String customerName = customer == null ? "?" : customer.getName();
        return "SalesOrder{id=" + id + ", status='" + status + "', total=" + total
                + ", customer=" + customerName + '}';
    }
}
