package com.entidaddesconocida.monte_erp.domain.model;

import java.util.Objects;

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
 * <p>The factory rejects a non-positive category or supplier id
 * <em>before</em> any query is issued. Unlike the former in-memory filter, these
 * criteria are applied in the SQL {@code WHERE}.</p>
 */
public final class ProductFilter {

    private static final ProductFilter ALL = new ProductFilter(null, null, null);

    private final String search;
    private final Integer categoryId;
    private final Integer supplierId;

    private ProductFilter(String search, Integer categoryId, Integer supplierId) {
        this.search = search;
        this.categoryId = categoryId;
        this.supplierId = supplierId;
    }

    /** Whole list: no text search, no category and no supplier. */
    public static ProductFilter all() {
        return ALL;
    }

    /**
     * Builds a filter with optional criteria.
     *
     * @param search     text matched against name or SKU; blank means no filter
     * @param categoryId category to filter by or {@code null} for every category
     * @param supplierId supplier to filter by or {@code null} for every supplier
     * @throws IllegalArgumentException when an id is not positive
     */
    public static ProductFilter of(String search, Integer categoryId, Integer supplierId) {
        String text = normalize(search);
        if (categoryId != null && categoryId <= 0) {
            throw new IllegalArgumentException(
                    "El identificador de la categoría debe ser positivo: " + categoryId);
        }
        if (supplierId != null && supplierId <= 0) {
            throw new IllegalArgumentException(
                    "El identificador del proveedor debe ser positivo: " + supplierId);
        }
        if (text == null && categoryId == null && supplierId == null) {
            return ALL;
        }
        return new ProductFilter(text, categoryId, supplierId);
    }

    /** Search text without surrounding blanks, or {@code null} when absent. */
    public String search() {
        return search;
    }

    /** Category id or {@code null} when every category is listed. */
    public Integer categoryId() {
        return categoryId;
    }

    /** Supplier id or {@code null} when every supplier is listed. */
    public Integer supplierId() {
        return supplierId;
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
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ProductFilter filter)) {
            return false;
        }
        return Objects.equals(search, filter.search)
                && Objects.equals(categoryId, filter.categoryId)
                && Objects.equals(supplierId, filter.supplierId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(search, categoryId, supplierId);
    }

    @Override
    public String toString() {
        return "ProductFilter{search=" + search + ", categoryId=" + categoryId
                + ", supplierId=" + supplierId + '}';
    }
}
