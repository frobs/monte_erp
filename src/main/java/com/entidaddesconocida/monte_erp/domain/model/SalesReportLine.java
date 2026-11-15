package com.entidaddesconocida.monte_erp.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Raw read model of a sales-order line inside a period, used as the input of
 * {@link com.entidaddesconocida.monte_erp.domain.service.SalesReportCalculator}
 * (RF-12 / CU-09, decisions D2/D9).
 *
 * <p>It is the flat projection the adapter returns ({@code SALES_ORDER} joined
 * with {@code SALES_ORDER_LINE} and {@code PRODUCT}): it keeps the order id, its
 * date and status plus the line product, quantity and unit price, so the
 * aggregation by product happens in the pure domain instead of in SQL. The status
 * travels with the line because excluding the cancelled orders is a
 * <strong>domain invariant</strong> (decision D3), not a {@code WHERE} clause.</p>
 */
public final class SalesReportLine {

    private final int orderId;
    private final LocalDateTime orderDate;
    private final OrderStatus status;
    private final int quantity;
    private final BigDecimal unitPrice;
    private final Product product;

    /**
     * @throws IllegalArgumentException when a mandatory value is {@code null},
     *                                  {@code quantity <= 0} or the unit price
     *                                  is negative; the read model mirrors the
     *                                  invariants of {@link SalesOrderLine}
     */
    public SalesReportLine(int orderId, LocalDateTime orderDate, OrderStatus status, int quantity,
                           BigDecimal unitPrice, Product product) {
        if (orderDate == null) {
            throw new IllegalArgumentException("La fecha del pedido es obligatoria");
        }
        if (status == null) {
            throw new IllegalArgumentException("El estado del pedido es obligatorio");
        }
        if (product == null) {
            throw new IllegalArgumentException("El producto de la línea es obligatorio");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "La cantidad vendida debe ser mayor que 0: " + quantity);
        }
        if (unitPrice == null || unitPrice.signum() < 0) {
            throw new IllegalArgumentException(
                    "El precio unitario no puede ser negativo: " + unitPrice);
        }
        this.orderId = orderId;
        this.orderDate = orderDate;
        this.status = status;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.product = product;
    }

    public int getOrderId() {
        return orderId;
    }

    public LocalDateTime getOrderDate() {
        return orderDate;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public Product getProduct() {
        return product;
    }

    /** Amount the line contributes: {@code unitPrice × quantity} with {@code BigDecimal}. */
    public BigDecimal subtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
