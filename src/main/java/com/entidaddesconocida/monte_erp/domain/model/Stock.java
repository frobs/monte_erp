package com.entidaddesconocida.monte_erp.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Stock lot (the {@code STOCK} table of the E-R diagram), introduced by RF-14 /
 * CU-10.
 *
 * <p>A lot is the <strong>individual</strong> entry of stock: it comes from one
 * purchase line with its own purchase price and its own entry date. The available
 * stock of a product is not an attribute of {@link Product}: it is computed by
 * adding the {@code availableQuantity} of its lots (CU-08, deferred to
 * US-09/US-14, decision D4).</p>
 *
 * <p>The lot is born <strong>available</strong> (decision D6): the creation
 * constructor requires {@code initialQuantity > 0} and
 * {@code purchasePrice >= 0} and sets {@code availableQuantity == initialQuantity},
 * mirroring the CHECK constraints of the DDL that the E-R documentation declares
 * validated in the application. Money is a {@link BigDecimal} (the column is
 * {@code DECIMAL(10, 2)}), never a {@code double}.</p>
 *
 * <p>{@code entryDate} defaults to {@code LocalDateTime.now()} on creation, but
 * the value of the database prevails when the lot is reloaded, exactly like
 * {@code SalesOrder.orderDate}.</p>
 */
public class Stock {

    private int id;
    private int initialQuantity;
    private int availableQuantity;
    private BigDecimal purchasePrice;
    private LocalDateTime entryDate;
    private Product product;
    private Purchase purchase;

    /**
     * Builds a brand new lot (no id): available from the start
     * ({@code availableQuantity == initialQuantity}) and entered now.
     *
     * @throws IllegalArgumentException when {@code initialQuantity <= 0} or the
     *                                  purchase price is {@code null}/negative,
     *                                  mirroring the {@code chk_stock_*} CHECK
     *                                  constraints of the schema
     */
    public Stock(Product product, int initialQuantity, BigDecimal purchasePrice) {
        if (initialQuantity <= 0) {
            throw new IllegalArgumentException(
                    "La cantidad inicial del lote debe ser mayor que 0: " + initialQuantity);
        }
        if (purchasePrice == null || purchasePrice.signum() < 0) {
            throw new IllegalArgumentException(
                    "El precio de compra del lote no puede ser negativo: " + purchasePrice);
        }
        this.product = product;
        this.initialQuantity = initialQuantity;
        this.availableQuantity = initialQuantity;
        this.purchasePrice = purchasePrice;
        this.entryDate = LocalDateTime.now();
    }

    /** Full constructor, used when mapping a {@code STOCK} row. */
    public Stock(int id, int initialQuantity, int availableQuantity, BigDecimal purchasePrice,
                 LocalDateTime entryDate, Product product, Purchase purchase) {
        this.id = id;
        this.initialQuantity = initialQuantity;
        this.availableQuantity = availableQuantity;
        this.purchasePrice = purchasePrice;
        this.entryDate = entryDate;
        this.product = product;
        this.purchase = purchase;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    /** Quantity the lot entered with; the only one that counts for the subtotal. */
    public int getInitialQuantity() {
        return initialQuantity;
    }

    public void setInitialQuantity(int initialQuantity) {
        this.initialQuantity = initialQuantity;
    }

    /**
     * Quantity still available in the lot. RF-14 only <strong>creates</strong>
     * lots, so it equals {@link #getInitialQuantity()}; the consumption (FIFO)
     * belongs to RF-15 (US-14, decision D4).
     */
    public int getAvailableQuantity() {
        return availableQuantity;
    }

    public void setAvailableQuantity(int availableQuantity) {
        this.availableQuantity = availableQuantity;
    }

    public BigDecimal getPurchasePrice() {
        return purchasePrice;
    }

    public void setPurchasePrice(BigDecimal purchasePrice) {
        this.purchasePrice = purchasePrice;
    }

    public LocalDateTime getEntryDate() {
        return entryDate;
    }

    public void setEntryDate(LocalDateTime entryDate) {
        this.entryDate = entryDate;
    }

    /** Product the lot stores (the {@code product_id} foreign key). */
    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    /** Purchase the lot comes from (the {@code purchase_id} foreign key). */
    public Purchase getPurchase() {
        return purchase;
    }

    public void setPurchase(Purchase purchase) {
        this.purchase = purchase;
    }

    /**
     * Amount the lot adds to the purchase total: {@code purchasePrice ×
     * initialQuantity} (0 when the price is missing).
     */
    public BigDecimal subtotal() {
        if (purchasePrice == null) {
            return BigDecimal.ZERO;
        }
        return purchasePrice.multiply(BigDecimal.valueOf(initialQuantity));
    }

    /**
     * Takes {@code quantity} units out of the lot, mirroring
     * {@code chk_stock_available_quantity} ({@code available >= 0}). The change
     * is in memory only: persisting it belongs to the repository that runs
     * inside the sale transaction (RF-15, decision D6).
     *
     * @throws IllegalArgumentException when {@code quantity <= 0} or there are
     *                                  not enough available units in the lot
     */
    public void consume(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "La cantidad a consumir debe ser mayor que 0: " + quantity);
        }
        if (quantity > availableQuantity) {
            throw new IllegalArgumentException("No se pueden consumir " + quantity
                    + " unidades del lote " + id + ": solo hay " + availableQuantity + " disponibles");
        }
        availableQuantity -= quantity;
    }

    /**
     * Returns {@code quantity} units to the lot, used when a sale is cancelled
     * (decision D2). The available quantity can never exceed the initial one,
     * mirroring {@code chk_stock_not_exceed_initial}.
     *
     * @throws IllegalArgumentException when {@code quantity <= 0} or the return
     *                                  would push the available quantity above
     *                                  the initial one
     */
    public void restore(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "La cantidad a reponer debe ser mayor que 0: " + quantity);
        }
        if (availableQuantity + quantity > initialQuantity) {
            throw new IllegalArgumentException("No se pueden reponer " + quantity
                    + " unidades al lote " + id + ": superaría la cantidad inicial "
                    + initialQuantity);
        }
        availableQuantity += quantity;
    }

    /** Readable rendering for logs/combos, e.g. {@code "3 × Teclado"}. */
    @Override
    public String toString() {
        String name = product == null ? "?" : product.getName();
        return initialQuantity + " × " + name;
    }
}
