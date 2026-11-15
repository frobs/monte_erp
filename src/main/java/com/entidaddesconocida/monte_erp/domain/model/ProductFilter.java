package com.entidaddesconocida.monte_erp.domain.model;

/**
 * Immutable value object holding the criteria the product list is filtered by
 * (RF-03 / RF-11).
 *
 * <p>Every criterion is optional and {@code null} means "no bound", so
 * {@link #all()} represents the whole list. The text search matches the product
 * name or SKU and is delegated to the database collation
 * ({@code utf8mb4_unicode_ci}), which makes it case- and accent-insensitive.
 * The category and supplier criteria travel as foreign keys: the supplier is not
 * a product column, so the adapter resolves it through the purchase history
 * ({@code STOCK JOIN PURCHASE}), keeping the full history including lots at 0
 * (decision D3).</p>
 *
 * <p>The canonical constructor normalizes the text and rejects a non-positive
 * category or supplier id <em>before</em> any query is issued. Unlike the former
 * in-memory filter, these criteria are applied in the SQL {@code WHERE}.</p>
 *
 * @param search     text matched against name or SKU; blank means no filter
 * @param categoryId category to filter by or {@code null} for every category
 * @param supplierId supplier to filter by or {@code null} for every supplier
 */
public record ProductFilter(String search, Integer categoryId, Integer supplierId) {

    private static final ProductFilter ALL = new ProductFilter(null, null, null);

    /**
     * @throws IllegalArgumentException when an id is not positive
     */
    public ProductFilter {
        search = normalize(search);
        if (categoryId != null && categoryId <= 0) {
            throw new IllegalArgumentException(
                    "El identificador de la categoría debe ser positivo: " + categoryId);
        }
        if (supplierId != null && supplierId <= 0) {
            throw new IllegalArgumentException(
                    "El identificador del proveedor debe ser positivo: " + supplierId);
        }
    }

    /** Whole list: no text search, no category and no supplier. */
    public static ProductFilter all() {
        return ALL;
    }

    /**
     * Builds a filter with optional criteria.
     *
     * @throws IllegalArgumentException when an id is not positive
     */
    public static ProductFilter of(String search, Integer categoryId, Integer supplierId) {
        return new ProductFilter(search, categoryId, supplierId);
    }

    /** Whether at least one criterion narrows the list. */
    public boolean isFiltered() {
        return search != null || categoryId != null || supplierId != null;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Override
    public String toString() {
        return "ProductFilter{search=" + search + ", categoryId=" + categoryId
                + ", supplierId=" + supplierId + '}';
    }
}
