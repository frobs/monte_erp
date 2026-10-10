package com.entidaddesconocida.monte_erp.domain.repository;

import com.entidaddesconocida.monte_erp.domain.model.Category;

import java.sql.SQLException;
import java.util.List;

/**
 * Read-only port over {@link Category} entities.
 *
 * <p>RF-03 only needs the categories to fill the product form and the filter
 * combo boxes; the full CRUD belongs to RF-04, so the write operations are
 * deliberately absent here.</p>
 */
public interface CategoryRepository {

    /** Returns every category, ordered by name. */
    List<Category> findAll() throws SQLException;
}
