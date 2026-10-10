package com.entidaddesconocida.monte_erp.viewmodel.support;

import com.entidaddesconocida.monte_erp.domain.model.Category;
import com.entidaddesconocida.monte_erp.domain.repository.CategoryRepository;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * In-memory double of {@link CategoryRepository} for unit tests.
 *
 * <p>Categorías se configuran directamente con {@link #withCategory(Category)};
 * {@link #throwOnAccess()} hace que {@link #findAll()} lance {@link SQLException}
 * y {@link #failOn(String)} permite fallar solo ese método. Las filas se ordenan
 * por nombre, igual que la tabla real.</p>
 */
public class FakeCategoryRepository implements CategoryRepository {

    private final List<Category> categories = new ArrayList<>();
    private final Set<String> failingMethods = new HashSet<>();
    private boolean throwOnAccess;
    private int nextId = 1;

    public FakeCategoryRepository withCategory(Category category) {
        if (category.getId() == 0) {
            category.setId(nextId++);
        }
        categories.add(category);
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

    @Override
    public List<Category> findAll() throws SQLException {
        if (throwOnAccess || failingMethods.contains("findAll")) {
            throw new SQLException("Simulated database failure in findAll");
        }
        return categories.stream()
                .sorted(Comparator.comparing(Category::getName))
                .toList();
    }
}
