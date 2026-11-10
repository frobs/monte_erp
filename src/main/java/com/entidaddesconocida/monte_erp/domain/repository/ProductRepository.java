package com.entidaddesconocida.monte_erp.domain.repository;

import com.entidaddesconocida.monte_erp.domain.model.Product;
import com.entidaddesconocida.monte_erp.domain.model.ProductFilter;

import java.sql.SQLException;
import java.util.List;

/**
 * Port that abstracts persistence of {@link Product} entities.
 *
 * <p>Implementations live in {@code infrastructure} (e.g.
 * {@code MySqlProductRepository}). The domain layer depends on this interface,
 * never on a concrete technology.</p>
 *
 * <p>Deleting a product is <strong>logical</strong> ({@link #deactivate(int)})
 * because {@code STOCK} and {@code SALES_ORDER_LINE} reference it with
 * {@code ON DELETE RESTRICT}.</p>
 */
public interface ProductRepository {

    /** Returns every active product, ordered by name. */
    List<Product> findAll() throws SQLException;

    /**
     * Returns the active products matching {@code filter}, ordered by name. The
     * filtering happens <strong>in the query</strong>: the text is compared with
     * name/SKU, the category with {@code PRODUCT.category_id} and the supplier
     * through the whole purchase history (a product matches when it has at least
     * one lot of a purchase from that supplier, decision D3).
     */
    List<Product> findAll(ProductFilter filter) throws SQLException;

    /** Returns every logically deleted product, ordered by name. */
    List<Product> findInactive() throws SQLException;

    /** Returns the logically deleted products matching {@code filter} (same rules as {@link #findAll(ProductFilter)}). */
    List<Product> findInactive(ProductFilter filter) throws SQLException;

    /**
     * Looks a product up by its SKU (unique column), regardless of its state,
     * so a deactivated product still reserves its SKU.
     *
     * @return the matching product, or {@code null} when it does not exist
     */
    Product findBySku(String sku) throws SQLException;

    /**
     * Inserts a new product.
     *
     * @return the generated identifier, which is also assigned to the object
     */
    int insert(Product product) throws SQLException;

    /**
     * Updates every editable field of an existing product, including
     * {@code description} and {@code min_stock} (not edited by RF-03 but
     * preserved so an edition never erases them).
     */
    void update(Product product) throws SQLException;

    /** Logical deletion: sets {@code active} to 0 without removing the row. */
    void deactivate(int id) throws SQLException;

    /** Brings a logically deleted product back to active. */
    void activate(int id) throws SQLException;
}
