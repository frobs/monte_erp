package com.entidaddesconocida.monte_erp.domain.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Line of a sales order (the {@code SALES_ORDER_LINE} table of the E-R diagram),
 * introduced by RF-07 / CU-07.
 *
 * <p>{@link #unitPrice} is a <strong>snapshot</strong> of {@link Product#getPrice()}
 * taken when the line is added (decision D4): an old order must keep the price it
 * was sold at even if the product price changes later. For the same reason the
 * money is a {@link BigDecimal} (the column is {@code DECIMAL(10, 2)}), never a
 * {@code double}.</p>
 *
 * <p>The stock is not checked here: it belongs to RF-09/RF-15 (US-09/US-14).
 * The lots the line was served from are exposed through {@link #getIssues()}
 * (RF-15), a read model loaded with the order detail; the sum of the issue
 * quantities equals {@link #getQuantity()}.</p>
 */
public class SalesOrderLine {

    private int id;
    private int quantity;
    private BigDecimal unitPrice;
    private Product product;

    /** Lots consumed by this line (RF-15); never {@code null}. */
    private List<StockIssue> issues = new ArrayList<>();

    /** Builds a not yet persisted line (no id) with the price snapshot. */
    public SalesOrderLine(int quantity, BigDecimal unitPrice, Product product) {
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.product = product;
    }

    /** Full constructor, used when mapping a {@code SALES_ORDER_LINE} row. */
    public SalesOrderLine(int id, int quantity, BigDecimal unitPrice, Product product) {
        this.id = id;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.product = product;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    /** Product sold in this line (the {@code product_id} foreign key). */
    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    /**
     * Lots the line was served from, in FIFO order (RF-15); never {@code null}.
     * It is a read model: writing the consumption belongs to the repository, in
     * the same transaction that saves the order.
     */
    public List<StockIssue> getIssues() {
        if (issues == null) {
            issues = new ArrayList<>();
        }
        return issues;
    }

    public void setIssues(List<StockIssue> issues) {
        this.issues = issues == null ? new ArrayList<>() : new ArrayList<>(issues);
    }

    /** Line total: {@code unitPrice × quantity} (0 when the price is missing). */
    public BigDecimal subtotal() {
        if (unitPrice == null) {
            return BigDecimal.ZERO;
        }
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    /** Readable rendering for combos/logs, e.g. {@code "2 × Teclado"}. */
    @Override
    public String toString() {
        String name = product == null ? "?" : product.getName();
        return quantity + " × " + name;
    }
}
