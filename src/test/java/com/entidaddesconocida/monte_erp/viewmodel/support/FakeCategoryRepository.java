package com.entidaddesconocida.monte_erp.viewmodel.support;

import com.entidaddesconocida.monte_erp.domain.model.Category;
import com.entidaddesconocida.monte_erp.domain.repository.CategoryRepository;
import com.entidaddesconocida.monte_erp.viewmodel.SearchText;

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
 * In-memory double of {@link CategoryRepository} for unit tests.
 *
 * <p>Categorías se configuran directamente con {@link #withCategory(Category)};
 * {@link #throwOnAccess()} hace que cualquier método lance {@link SQLException}
 * y {@link #failOn(String)} permite fallar solo ese método. El doble guarda
 * <em>copias</em> de las filas, ordena por nombre y trata el nombre como
 * case-insensitive, igual que la colación {@code utf8mb4_unicode_ci} de la tabla
 * real. {@link #insert(Category)} simula la clave única {@code uq_category_name}.</p>
 */
public class FakeCategoryRepository implements CategoryRepository {

    private final Map<Integer, Category> categories = new LinkedHashMap<>();
    private final Set<String> failingMethods = new HashSet<>();
    private boolean throwOnAccess;
    private int nextId = 1;

    public FakeCategoryRepository withCategory(Category category) {
        if (category.getId() == 0) {
            category.setId(nextId++);
        }
        categories.put(category.getId(), copy(category));
        return this;
    }

    public FakeCategoryRepository throwOnAccess() {
        this.throwOnAccess = true;
        return this;
    }

    /** Makes every future call to {@code method} fail, keeping the rest usable. */
    public FakeCategoryRepository failOn(String method) {
        failingMethods.add(method);
        return this;
    }

    private void maybeThrow(String method) throws SQLException {
        if (throwOnAccess || failingMethods.contains(method)) {
            throw new SQLException("Simulated database failure in " + method);
        }
    }

    @Override
    public List<Category> findAll() throws SQLException {
        maybeThrow("findAll");
        return ordered(category -> true);
    }

    @Override
    public List<Category> findActive() throws SQLException {
        maybeThrow("findActive");
        return ordered(Category::isActive);
    }

    @Override
    public List<Category> findActive(String search) throws SQLException {
        maybeThrow("findActive");
        return ordered(category -> category.isActive() && matches(category, search));
    }

    @Override
    public List<Category> findInactive() throws SQLException {
        maybeThrow("findInactive");
        return ordered(category -> !category.isActive());
    }

    @Override
    public List<Category> findInactive(String search) throws SQLException {
        maybeThrow("findInactive");
        return ordered(category -> !category.isActive() && matches(category, search));
    }

    @Override
    public Category findByName(String name) throws SQLException {
        maybeThrow("findByName");
        return categories.values().stream()
                .filter(category -> key(category.getName()).equals(key(name)))
                .findFirst()
                .map(FakeCategoryRepository::copy)
                .orElse(null);
    }

    @Override
    public int insert(Category category) throws SQLException {
        maybeThrow("insert");
        if (findByName(category.getName()) != null) {
            throw new SQLIntegrityConstraintViolationException(
                    "Duplicate entry for key 'uq_category_name': " + category.getName());
        }
        category.setId(nextId++);
        categories.put(category.getId(), copy(category));
        return category.getId();
    }

    @Override
    public void update(Category category) throws SQLException {
        maybeThrow("update");
        Category clash = findByName(category.getName());
        if (clash != null && clash.getId() != category.getId()) {
            throw new SQLIntegrityConstraintViolationException(
                    "Duplicate entry for key 'uq_category_name': " + category.getName());
        }
        categories.put(category.getId(), copy(category));
    }

    @Override
    public void deactivate(int id) throws SQLException {
        maybeThrow("deactivate");
        Category stored = categories.get(id);
        if (stored != null) {
            stored.setActive(false);
        }
    }

    @Override
    public void activate(int id) throws SQLException {
        maybeThrow("activate");
        Category stored = categories.get(id);
        if (stored != null) {
            stored.setActive(true);
        }
    }

    private List<Category> ordered(java.util.function.Predicate<Category> filter) {
        List<Category> result = new ArrayList<>();
        categories.values().stream()
                .filter(filter)
                .sorted(Comparator.comparing(Category::getName))
                .map(FakeCategoryRepository::copy)
                .forEach(result::add);
        return result;
    }

    private static String key(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }

    private static boolean matches(Category category, String search) {
        return SearchText.matches(search, category.getName(), category.getDescription());
    }

    private static Category copy(Category category) {
        Category copy = new Category(category.getId(), category.getName(),
                category.getDescription(), category.isActive());
        return copy;
    }
}
