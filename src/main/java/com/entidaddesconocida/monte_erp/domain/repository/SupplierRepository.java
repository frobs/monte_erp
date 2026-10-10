package com.entidaddesconocida.monte_erp.domain.repository;

import com.entidaddesconocida.monte_erp.domain.model.Supplier;

import java.sql.SQLException;
import java.util.List;

/**
 * Read-only port over {@link Supplier} entities.
 *
 * <p>RF-03 only needs the suppliers to fill the product form and the filter
 * combo boxes; the full CRUD belongs to RF-05, so the write operations are
 * deliberately absent here.</p>
 */
public interface SupplierRepository {

    /** Returns every supplier, ordered by name. */
    List<Supplier> findAll() throws SQLException;
}
