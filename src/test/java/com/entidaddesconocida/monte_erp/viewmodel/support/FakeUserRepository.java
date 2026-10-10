package com.entidaddesconocida.monte_erp.viewmodel.support;

import com.entidaddesconocida.monte_erp.domain.model.User;
import com.entidaddesconocida.monte_erp.domain.repository.UserRepository;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * In-memory double of {@link UserRepository} for unit tests.
 *
 * <p>Usuarios se configuran directamente; {@link #throwOnAccess()} hace que
 * cualquier método lance {@link SQLException} y {@link #failOn(String)} permite
 * fallar solo un método (p. ej. {@code "update"}). El doble guarda <em>copias</em>
 * de las filas y las ordena por {@code username}, igual que la tabla real; como
 * la columna usa la colación {@code utf8mb4_unicode_ci}, el nombre de usuario se
 * trata como case-insensitive.</p>
 */
public class FakeUserRepository implements UserRepository {

    private final Map<String, User> users = new LinkedHashMap<>();
    private final Set<String> failingMethods = new HashSet<>();
    private boolean throwOnAccess;
    private int nextId = 1;

    public FakeUserRepository withUser(User user) {
        if (user.getId() == 0) {
            user.setId(nextId++);
        }
        users.put(key(user.getUsername()), copy(user));
        return this;
    }

    public FakeUserRepository throwOnAccess() {
        this.throwOnAccess = true;
        return this;
    }

    /** Makes every future call to {@code method} fail, keeping the rest usable. */
    public FakeUserRepository failOn(String method) {
        failingMethods.add(method);
        return this;
    }

    private void maybeThrow(String method) throws SQLException {
        if (throwOnAccess || failingMethods.contains(method)) {
            throw new SQLException("Simulated database failure in " + method);
        }
    }

    @Override
    public User findByUsername(String username) throws SQLException {
        maybeThrow("findByUsername");
        User stored = users.get(key(username));
        return stored == null ? null : copy(stored);
    }

    @Override
    public List<User> findAll() throws SQLException {
        maybeThrow("findAll");
        List<User> result = new ArrayList<>();
        users.values().stream()
                .sorted(Comparator.comparing(User::getUsername))
                .map(FakeUserRepository::copy)
                .forEach(result::add);
        return result;
    }

    @Override
    public int insert(User user) throws SQLException {
        maybeThrow("insert");
        if (users.containsKey(key(user.getUsername()))) {
            throw new SQLIntegrityConstraintViolationException(
                    "Duplicate entry for key 'uq_user_username': " + user.getUsername());
        }
        user.setId(nextId++);
        users.put(key(user.getUsername()), copy(user));
        return user.getId();
    }

    @Override
    public void update(User user) throws SQLException {
        maybeThrow("update");
        User clash = users.get(key(user.getUsername()));
        if (clash != null && clash.getId() != user.getId()) {
            throw new SQLIntegrityConstraintViolationException(
                    "Duplicate entry for key 'uq_user_username': " + user.getUsername());
        }
        users.values().removeIf(existing -> existing.getId() == user.getId());
        users.put(key(user.getUsername()), copy(user));
    }

    @Override
    public void deactivate(int id) throws SQLException {
        maybeThrow("deactivate");
        for (User u : users.values()) {
            if (u.getId() == id) {
                u.setActive(false);
                return;
            }
        }
    }

    private static String key(String username) {
        return username == null ? "" : username.toLowerCase(Locale.ROOT);
    }

    private static User copy(User user) {
        User copy = new User(user.getName(), user.getUsername(), user.getPasswordHash(),
                user.getRole(), user.isActive());
        copy.setId(user.getId());
        return copy;
    }
}
