package com.entidaddesconocida.monte_erp.viewmodel.support;

import com.entidaddesconocida.monte_erp.domain.model.User;
import com.entidaddesconocida.monte_erp.domain.repository.UserRepository;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory double of {@link UserRepository} for unit tests.
 *
 * <p>Usuarios se configuran directamente; llamar a {@link #throwOnAccess()} hace
 * que cualquier método lance {@link SQLException}.</p>
 */
public class FakeUserRepository implements UserRepository {

    private final Map<String, User> users = new LinkedHashMap<>();
    private final AtomicInteger nextId = new AtomicInteger(1);
    private boolean throwOnAccess;

    public FakeUserRepository withUser(User user) {
        users.put(user.getUsername(), user);
        return this;
    }

    public FakeUserRepository throwOnAccess() {
        this.throwOnAccess = true;
        return this;
    }

    private void maybeThrow() throws SQLException {
        if (throwOnAccess) {
            throw new SQLException("Simulated database failure");
        }
    }

    @Override
    public User findByUsername(String username) throws SQLException {
        maybeThrow();
        return users.get(username);
    }

    @Override
    public List<User> findAll() throws SQLException {
        maybeThrow();
        return new ArrayList<>(users.values());
    }

    @Override
    public int insert(User user) throws SQLException {
        maybeThrow();
        int id = nextId.getAndIncrement();
        user.setId(id);
        users.put(user.getUsername(), user);
        return id;
    }

    @Override
    public void update(User user) throws SQLException {
        maybeThrow();
        users.put(user.getUsername(), user);
    }

    @Override
    public void deactivate(int id) throws SQLException {
        maybeThrow();
        for (User u : users.values()) {
            if (u.getId() == id) {
                u.setActive(false);
                return;
            }
        }
    }
}
