package com.entidaddesconocida.monte_erp.domain.model;

/**
 * Read model of the stock screen (CU-08 / RF-09 / RF-10): a {@link Product}
 * together with its <strong>aggregated</strong> available quantity, the sum of
 * the available quantities of its lots.
 *
 * <p>It is immutable on purpose: it is a snapshot computed by the repository,
 * not an entity. The minimum-stock rule is not duplicated here, it delegates to
 * {@link Product#isBelowMinimum(int)} (decision D8), so the alert has a single
 * implementation.</p>
 */
public final class StockLevel {

    /** Label shown in the table while the product is below its minimum (RF-10). */
    public static final String ALERT_LABEL = "Bajo mínimo";

    /** Label shown in the table while the product is at or above its minimum. */
    public static final String OK_LABEL = "OK";

    private final Product product;
    private final int availableQuantity;

    public StockLevel(Product product, int availableQuantity) {
        this.product = product;
        this.availableQuantity = availableQuantity;
    }

    public Product getProduct() {
        return product;
    }

    /** Sum of the available quantities of the product lots. */
    public int getAvailableQuantity() {
        return availableQuantity;
    }

    /** Minimum stock configured on the product (RF-03). */
    public int getMinimumStock() {
        return product == null ? 0 : product.getMinStock();
    }

    /** {@code true} when the available quantity is strictly below the minimum (RF-10). */
    public boolean isBelowMinimum() {
        return product != null && product.isBelowMinimum(availableQuantity);
    }

    /** Human readable alert cell: {@link #ALERT_LABEL} or {@link #OK_LABEL}. */
    public String alertLabel() {
        return isBelowMinimum() ? ALERT_LABEL : OK_LABEL;
    }

    @Override
    public String toString() {
        String name = product == null ? "?" : product.getName();
        return name + " (" + availableQuantity + " disponible/s, mínimo " + getMinimumStock() + ")";
    }
}
