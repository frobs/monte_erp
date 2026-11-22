package com.entidaddesconocida.monte_erp.domain.repository;

import com.entidaddesconocida.monte_erp.domain.model.Contact;

import java.sql.SQLException;
import java.util.List;

/**
 * Port with the operations shared by the contact entities (customers and
 * suppliers).
 *
 * <p>Both repositories expose the same contract, so the contact screens are
 * written once against this interface. The concrete repository still decides
 * the table, the columns and the SQL.</p>
 *
 * @param <T> contact entity managed by the repository
 */
public interface ContactRepository<T extends Contact> {

    /** Returns the active contacts matching {@code search}, ordered by name. */
    List<T> findActive(String search) throws SQLException;

    /** Returns the logically deleted contacts matching {@code search}, ordered by name. */
    List<T> findInactive(String search) throws SQLException;

    /** Returns the contact with the given name in any state, or {@code null}. */
    T findByName(String name) throws SQLException;

    /** Inserts a new contact (it is born active) and returns its generated id. */
    int insert(T contact) throws SQLException;

    /** Updates the contact data; the {@code active} flag is left untouched. */
    void update(T contact) throws SQLException;

    /** Logical deletion: sets {@code active = 0}. */
    void deactivate(int id) throws SQLException;

    /** Reactivates a previously deleted contact: sets {@code active = 1}. */
    void activate(int id) throws SQLException;
}
