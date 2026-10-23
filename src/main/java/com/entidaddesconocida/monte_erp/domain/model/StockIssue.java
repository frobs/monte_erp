package com.entidaddesconocida.monte_erp.domain.model;

/**
 * Consumption of a lot by a sales-order line (the {@code STOCK_ISSUE} table of
 * the E-R diagram), introduced by RF-15 / CU-11.
 *
 * <p>One order line may be served from several lots (FIFO), so every row records
 * how many units were taken from one specific lot. The collection of issues of a
 * line is the persistent evidence of which lots were sold and how much was taken
 * from each one: the sum of {@link #quantity} over the issues of a line equals
 * the line quantity (business rule the DDL cannot express).</p>
 *
 * <p>{@link #quantity} must be strictly positive, mirroring the
 * {@code chk_stock_issue_quantity} CHECK constraint of the schema. The negative
 * case (returning stock) is not modelled here: cancelling an order deletes its
 * issues, it does not store negative movements.</p>
 */
public class StockIssue {

    private int id;
    private int quantity;
    private SalesOrderLine salesOrderLine;
    private Stock stock;

    /**
     * Builds a not yet persisted issue (no id).
     *
     * @throws IllegalArgumentException when {@code quantity <= 0}, mirroring the
     *                                  {@code chk_stock_issue_quantity} CHECK
     */
    public StockIssue(int quantity, SalesOrderLine salesOrderLine, Stock stock) {
        setQuantity(quantity);
        this.salesOrderLine = salesOrderLine;
        this.stock = stock;
    }

    /** Full constructor, used when mapping a {@code STOCK_ISSUE} row. */
    public StockIssue(int id, int quantity, SalesOrderLine salesOrderLine, Stock stock) {
        setQuantity(quantity);
        this.id = id;
        this.salesOrderLine = salesOrderLine;
        this.stock = stock;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    /** Units taken from the lot for the line; always greater than 0. */
    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "La cantidad consumida de un lote debe ser mayor que 0: " + quantity);
        }
        this.quantity = quantity;
    }

    /** Line the units belong to (the {@code sales_order_line_id} foreign key). */
    public SalesOrderLine getSalesOrderLine() {
        return salesOrderLine;
    }

    public void setSalesOrderLine(SalesOrderLine salesOrderLine) {
        this.salesOrderLine = salesOrderLine;
    }

    /** Lot the units came from (the {@code stock_id} foreign key). */
    public Stock getStock() {
        return stock;
    }

    public void setStock(Stock stock) {
        this.stock = stock;
    }

    @Override
    public String toString() {
        return "StockIssue{quantity=" + quantity + ", stockId="
                + (stock == null ? "?" : stock.getId()) + '}';
    }
}
