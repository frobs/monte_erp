package com.entidaddesconocida.monte_erp.domain.repository;

import com.entidaddesconocida.monte_erp.domain.model.Supplier;

import java.sql.SQLException;
import java.util.List;

/**
 * Port over {@link Supplier} entities (RF-05 / CU-05).
 *
 * <p>{@link #findAll()} keeps the "every supplier" semantics used by the product
 * filter of RF-11, so an inactive supplier is still listed there.
 * {@link #findActive()} is the list for the assignment combo of a purchase
 * (RF-14): an inactive supplier cannot be assigned.</p>
 *
 * <p>Suppliers are never deleted physically (decision D1): {@link #deactivate(int)}
 * just flags the row, because {@code PURCHASE} references {@code SUPPLIER} with
 * {@code ON DELETE RESTRICT}.</p>
 */
public interface SupplierRepository extends ContactRepository<Supplier> {

    /** Returns every supplier (active and inactive), ordered by name. */
    List<Supplier> findAll() throws SQLException;

    /** Returns only the active suppliers, ordered by name. */
    List<Supplier> findActive() throws SQLException;

    /**
     * Returns the active suppliers whose name, phone or email contains
     * {@code search}, ordered by name. The filtering happens <strong>in the
     * query</strong>; a blank or {@code null} term lists every active supplier.
     */
    List<Supplier> findActive(String search) throws SQLException;

    /** Returns only the logically deleted suppliers, ordered by name. */
    List<Supplier> findInactive() throws SQLException;

    /** Returns the logically deleted suppliers matching {@code search} (same rules as {@link #findActive(String)}). */
    List<Supplier> findInactive(String search) throws SQLException;

    /** Returns the supplier with the given name in any state, or {@code null}. */
    Supplier findByName(String name) throws SQLException;

    /** Inserts a new supplier (it is born active) and returns its generated id. */
    int insert(Supplier supplier) throws SQLException;

    /** Updates name, phone and email; the {@code active} flag is left untouched. */
    void update(Supplier supplier) throws SQLException;

    /** Logical deletion: sets {@code active = 0}. */
    void deactivate(int id) throws SQLException;

    /** Reactivates a previously deleted supplier: sets {@code active = 1}. */
    void activate(int id) throws SQLException;
}
