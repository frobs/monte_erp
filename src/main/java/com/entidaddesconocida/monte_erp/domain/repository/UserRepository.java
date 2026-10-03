package com.entidaddesconocida.monte_erp.domain.repository;

import com.entidaddesconocida.monte_erp.domain.model.User;

import java.sql.SQLException;
import java.util.List;

/**
 * Port that abstracts persistence of {@link User} entities.
 *
 * <p>Implementations live in {@code infrastructure} (e.g.
 * {@code MySqlUserRepository}). The domain layer depends on this interface,
 * never on a concrete technology.</p>
 */
public interface UserRepository {

    /**
     * Looks a user up by its login name (unique column {@code username}).
     *
     * @return the matching user, or {@code null} when it does not exist
     */
    User findByUsername(String username) throws SQLException;

    /** Returns every user, ordered by login name. */
    List<User> findAll() throws SQLException;

    /**
     * Inserts a new user.
     *
     * @return the generated identifier, which is also assigned to the object
     */
    int insert(User user) throws SQLException;

    /** Updates every editable field of an existing user. */
    void update(User user) throws SQLException;

    /**
     * Logical deletion: sets {@code active} to 0 without removing the row, so
     * the foreign keys of the purchases and orders already recorded stay valid.
     */
    void deactivate(int id) throws SQLException;
}
