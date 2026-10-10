package com.entidaddesconocida.monte_erp.viewmodel.support;

import com.entidaddesconocida.monte_erp.domain.model.Supplier;
import com.entidaddesconocida.monte_erp.domain.repository.SupplierRepository;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * In-memory double of {@link SupplierRepository} for unit tests.
 *
 * <p>Proveedores se configuran directamente con {@link #withSupplier(Supplier)};
 * {@link #throwOnAccess()} hace que {@link #findAll()} lance {@link SQLException}
 * y {@link #failOn(String)} permite fallar solo ese método. Las filas se ordenan
 * por nombre, igual que la tabla real.</p>
 */
public class FakeSupplierRepository implements SupplierRepository {

    private final List<Supplier> suppliers = new ArrayList<>();
    private final Set<String> failingMethods = new HashSet<>();
    private boolean throwOnAccess;
    private int nextId = 1;

    public FakeSupplierRepository withSupplier(Supplier supplier) {
        if (supplier.getId() == 0) {
            supplier.setId(nextId++);
        }
        suppliers.add(supplier);
        return this;
    }

    public FakeSupplierRepository throwOnAccess() {
        this.throwOnAccess = true;
        return this;
    }

    /** Makes every future call to {@code method} fail, keeping the rest usable. */
    public FakeSupplierRepository failOn(String method) {
        failingMethods.add(method);
        return this;
    }

    @Override
    public List<Supplier> findAll() throws SQLException {
        if (throwOnAccess || failingMethods.contains("findAll")) {
            throw new SQLException("Simulated database failure in findAll");
        }
        return suppliers.stream()
                .sorted(Comparator.comparing(Supplier::getName))
                .toList();
    }
}
