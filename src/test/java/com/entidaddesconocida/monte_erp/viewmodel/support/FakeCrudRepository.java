package com.entidaddesconocida.monte_erp.viewmodel.support;

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
import java.util.function.Predicate;

/**
 * In-memory double driving {@code CrudViewModelTest}: it exercises the generic
 * CRUD template (load active/inactive, duplicate name, deactivate/activate and
 * simulated failures) without a database.
 *
 * <p>Mirrors the behaviour of the real fakes: it stores copies, sorts by name,
 * treats the name as case-insensitive and simulates the unique constraint with
 * {@link SQLIntegrityConstraintViolationException}. {@link #failOn(String)} makes
 * a single method fail and {@link #throwOnAccess()} makes every method fail.</p>
 */
public class FakeCrudRepository {

    private final Map<Integer, FakeCrudItem> items = new LinkedHashMap<>();
    private final Set<String> failingMethods = new HashSet<>();
    private boolean throwOnAccess;
    private int nextId = 1;

    public FakeCrudRepository withItem(FakeCrudItem item) {
        if (item.getId() == 0) {
            item.setId(nextId++);
        }
        items.put(item.getId(), copy(item));
        return this;
    }

    public FakeCrudRepository throwOnAccess() {
        this.throwOnAccess = true;
        return this;
    }

    /** Makes every future call to {@code method} fail, keeping the rest usable. */
    public FakeCrudRepository failOn(String method) {
        failingMethods.add(method);
        return this;
    }

    public int count() {
        return items.size();
    }

    private void maybeThrow(String method) throws SQLException {
        if (throwOnAccess || failingMethods.contains(method)) {
            throw new SQLException("Simulated database failure in " + method);
        }
    }

    public List<FakeCrudItem> findActive() throws SQLException {
        maybeThrow("findActive");
        return ordered(FakeCrudItem::isActive);
    }

    public List<FakeCrudItem> findInactive() throws SQLException {
        maybeThrow("findInactive");
        return ordered(item -> !item.isActive());
    }

    public FakeCrudItem findByName(String name) throws SQLException {
        maybeThrow("findByName");
        return items.values().stream()
                .filter(item -> key(item.getName()).equals(key(name)))
                .findFirst()
                .map(FakeCrudRepository::copy)
                .orElse(null);
    }

    public int insert(FakeCrudItem item) throws SQLException {
        maybeThrow("insert");
        if (findByName(item.getName()) != null) {
            throw new SQLIntegrityConstraintViolationException(
                    "Duplicate entry for key 'uq_fake_name': " + item.getName());
        }
        item.setId(nextId++);
        items.put(item.getId(), copy(item));
        return item.getId();
    }

    public void update(FakeCrudItem item) throws SQLException {
        maybeThrow("update");
        FakeCrudItem clash = findByName(item.getName());
        if (clash != null && clash.getId() != item.getId()) {
            throw new SQLIntegrityConstraintViolationException(
                    "Duplicate entry for key 'uq_fake_name': " + item.getName());
        }
        items.put(item.getId(), copy(item));
    }

    public void deactivate(int id) throws SQLException {
        maybeThrow("deactivate");
        FakeCrudItem stored = items.get(id);
        if (stored != null) {
            stored.setActive(false);
        }
    }

    public void activate(int id) throws SQLException {
        maybeThrow("activate");
        FakeCrudItem stored = items.get(id);
        if (stored != null) {
            stored.setActive(true);
        }
    }

    private List<FakeCrudItem> ordered(Predicate<FakeCrudItem> filter) {
        List<FakeCrudItem> result = new ArrayList<>();
        items.values().stream()
                .filter(filter)
                .sorted(Comparator.comparing(FakeCrudItem::getName))
                .map(FakeCrudRepository::copy)
                .forEach(result::add);
        return result;
    }

    private static String key(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }

    private static FakeCrudItem copy(FakeCrudItem item) {
        FakeCrudItem copy = new FakeCrudItem(item.getName(), item.isActive());
        copy.setId(item.getId());
        return copy;
    }
}
