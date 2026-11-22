package com.entidaddesconocida.monte_erp.domain.repository;

import com.entidaddesconocida.monte_erp.domain.model.Customer;

import java.sql.SQLException;
import java.util.List;

/**
 * Port over {@link Customer} entities (RF-06 / CU-06).
 *
 * <p>{@link #findAll()} keeps the "every customer" semantics used by the order
 * history/filters, so an inactive customer is still listed there.
 * {@link #findActive()} is the list for the customer combo of a sales order
 * (RF-07): an inactive customer cannot be assigned.</p>
 *
 * <p>Customers are never deleted physically (decision D1): {@link #deactivate(int)}
 * just flags the row, because {@code SALES_ORDER} references {@code CUSTOMER} with
 * {@code ON DELETE RESTRICT}.</p>
 */
public interface CustomerRepository extends ContactRepository<Customer> {

    /** Returns every customer (active and inactive), ordered by name. */
    List<Customer> findAll() throws SQLException;

    /** Returns only the active customers, ordered by name. */
    List<Customer> findActive() throws SQLException;

    /**
     * Returns the active customers whose name, phone or email contains
     * {@code search}, ordered by name. The filtering happens <strong>in the
     * query</strong>; a blank or {@code null} term lists every active customer.
     */
    List<Customer> findActive(String search) throws SQLException;

    /** Returns only the logically deleted customers, ordered by name. */
    List<Customer> findInactive() throws SQLException;

    /** Returns the logically deleted customers matching {@code search} (same rules as {@link #findActive(String)}). */
    List<Customer> findInactive(String search) throws SQLException;

    /** Returns the customer with the given name in any state, or {@code null}. */
    Customer findByName(String name) throws SQLException;

    /** Inserts a new customer (it is born active) and returns its generated id. */
    int insert(Customer customer) throws SQLException;

    /** Updates name, phone, email and address; the {@code active} flag is left untouched. */
    void update(Customer customer) throws SQLException;

    /** Logical deletion: sets {@code active = 0}. */
    void deactivate(int id) throws SQLException;

    /** Reactivates a previously deleted customer: sets {@code active = 1}. */
    void activate(int id) throws SQLException;
}
