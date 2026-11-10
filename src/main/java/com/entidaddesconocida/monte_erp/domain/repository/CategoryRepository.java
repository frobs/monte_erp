package com.entidaddesconocida.monte_erp.domain.repository;

import com.entidaddesconocida.monte_erp.domain.model.Category;

import java.sql.SQLException;
import java.util.List;

/**
 * Port over {@link Category} entities (RF-04 / CU-04).
 *
 * <p>{@link #findAll()} keeps the "every category" semantics used by the product
 * filter of RF-11, so an inactive category is still listed there.
 * {@link #findActive()} is the list for the assignment combo of a product: an
 * inactive category cannot be assigned (RF-04, decision D4).</p>
 *
 * <p>Categories are never deleted physically (decision D2): {@link #deactivate(int)}
 * just flags the row, because {@code PRODUCT} references {@code CATEGORY} with
 * {@code ON DELETE RESTRICT}.</p>
 */
public interface CategoryRepository {

    /** Returns every category (active and inactive), ordered by name. */
    List<Category> findAll() throws SQLException;

    /** Returns only the active categories, ordered by name. */
    List<Category> findActive() throws SQLException;

    /**
     * Returns the active categories whose name or description contains
     * {@code search}, ordered by name. The filtering happens <strong>in the
     * query</strong>; a blank or {@code null} term lists every active category.
     */
    List<Category> findActive(String search) throws SQLException;

    /** Returns only the logically deleted categories, ordered by name. */
    List<Category> findInactive() throws SQLException;

    /** Returns the logically deleted categories matching {@code search} (same rules as {@link #findActive(String)}). */
    List<Category> findInactive(String search) throws SQLException;

    /** Returns the category with the given name in any state, or {@code null}. */
    Category findByName(String name) throws SQLException;

    /** Inserts a new category (it is born active) and returns its generated id. */
    int insert(Category category) throws SQLException;

    /** Updates name and description; the {@code active} flag is left untouched. */
    void update(Category category) throws SQLException;

    /** Logical deletion: sets {@code active = 0}. */
    void deactivate(int id) throws SQLException;

    /** Reactivates a previously deleted category: sets {@code active = 1}. */
    void activate(int id) throws SQLException;
}
