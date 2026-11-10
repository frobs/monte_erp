package com.entidaddesconocida.monte_erp.domain.model;

/**
 * Read model of the product ↔ supplier projection (RF-11 / CU-03): a pair
 * {@code (productId, supplierId)} derived from the purchase history
 * ({@code PRODUCT} → {@code STOCK} → {@code PURCHASE} → {@code SUPPLIER}).
 *
 * <p>The supplier is not an attribute of {@link Product} and there is no direct
 * relation between the two tables, so the relation is derived from the
 * purchase/lot history (RF-14). A product shows up with a supplier when it has at
 * least one lot of a purchase from that supplier, regardless of the lot being
 * available or not (decision D3). The product screen resolves the supplier filter
 * directly in its SQL query; this read model exposes the same relation.</p>
 *
 * <p>It is immutable on purpose: it is a snapshot computed by the repository, not
 * an entity. Both ids must be positive, mirroring the foreign keys
 * {@code STOCK.product_id} and {@code PURCHASE.supplier_id}: a pair with a
 * non-positive id does not represent a real row. {@link #equals(Object)} and
 * {@link #hashCode()} are defined by both ids so the pairs can be stored in a
 * {@code Set}.</p>
 */
public final class ProductSupplier {

    private final int productId;
    private final int supplierId;

    /**
     * @throws IllegalArgumentException when either id is not positive
     */
    public ProductSupplier(int productId, int supplierId) {
        if (productId <= 0 || supplierId <= 0) {
            throw new IllegalArgumentException(
                    "Los identificadores de producto y proveedor deben ser positivos: producto="
                            + productId + ", proveedor=" + supplierId);
        }
        this.productId = productId;
        this.supplierId = supplierId;
    }

    public int getProductId() {
        return productId;
    }

    public int getSupplierId() {
        return supplierId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ProductSupplier pair)) {
            return false;
        }
        return productId == pair.productId && supplierId == pair.supplierId;
    }

    @Override
    public int hashCode() {
        return 31 * productId + supplierId;
    }

    @Override
    public String toString() {
        return "ProductSupplier{productId=" + productId + ", supplierId=" + supplierId + '}';
    }
}
