package com.entidaddesconocida.monte_erp.viewmodel.support;

import com.entidaddesconocida.monte_erp.domain.model.Supplier;
import com.entidaddesconocida.monte_erp.domain.repository.SupplierRepository;

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
 * In-memory double of {@link SupplierRepository} for unit tests.
 *
 * <p>Proveedores se configuran directamente con {@link #withSupplier(Supplier)};
 * {@link #throwOnAccess()} hace que cualquier método lance {@link SQLException}
 * y {@link #failOn(String)} permite fallar solo ese método. El doble guarda
 * <em>copias</em> de las filas, ordena por nombre y trata el nombre como
 * case-insensitive, igual que la colación {@code utf8mb4_unicode_ci} de la tabla
 * real. {@link #insert(Supplier)} simula la clave única {@code uq_supplier_name}.</p>
 */
public class FakeSupplierRepository implements SupplierRepository {

    private final Map<Integer, Supplier> suppliers = new LinkedHashMap<>();
    private final Set<String> failingMethods = new HashSet<>();
    private boolean throwOnAccess;
    private int nextId = 1;

    public FakeSupplierRepository withSupplier(Supplier supplier) {
        if (supplier.getId() == 0) {
            supplier.setId(nextId++);
        }
        suppliers.put(supplier.getId(), copy(supplier));
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

    private void maybeThrow(String method) throws SQLException {
        if (throwOnAccess || failingMethods.contains(method)) {
            throw new SQLException("Simulated database failure in " + method);
        }
    }

    @Override
    public List<Supplier> findAll() throws SQLException {
        maybeThrow("findAll");
        return ordered(supplier -> true);
    }

    @Override
    public List<Supplier> findActive() throws SQLException {
        maybeThrow("findActive");
        return ordered(Supplier::isActive);
    }

    @Override
    public List<Supplier> findInactive() throws SQLException {
        maybeThrow("findInactive");
        return ordered(supplier -> !supplier.isActive());
    }

    @Override
    public Supplier findByName(String name) throws SQLException {
        maybeThrow("findByName");
        return suppliers.values().stream()
                .filter(supplier -> key(supplier.getName()).equals(key(name)))
                .findFirst()
                .map(FakeSupplierRepository::copy)
                .orElse(null);
    }

    @Override
    public int insert(Supplier supplier) throws SQLException {
        maybeThrow("insert");
        if (findByName(supplier.getName()) != null) {
            throw new SQLIntegrityConstraintViolationException(
                    "Duplicate entry for key 'uq_supplier_name': " + supplier.getName());
        }
        supplier.setId(nextId++);
        suppliers.put(supplier.getId(), copy(supplier));
        return supplier.getId();
    }

    @Override
    public void update(Supplier supplier) throws SQLException {
        maybeThrow("update");
        Supplier clash = findByName(supplier.getName());
        if (clash != null && clash.getId() != supplier.getId()) {
            throw new SQLIntegrityConstraintViolationException(
                    "Duplicate entry for key 'uq_supplier_name': " + supplier.getName());
        }
        suppliers.put(supplier.getId(), copy(supplier));
    }

    @Override
    public void deactivate(int id) throws SQLException {
        maybeThrow("deactivate");
        Supplier stored = suppliers.get(id);
        if (stored != null) {
            stored.setActive(false);
        }
    }

    @Override
    public void activate(int id) throws SQLException {
        maybeThrow("activate");
        Supplier stored = suppliers.get(id);
        if (stored != null) {
            stored.setActive(true);
        }
    }

    private List<Supplier> ordered(java.util.function.Predicate<Supplier> filter) {
        List<Supplier> result = new ArrayList<>();
        suppliers.values().stream()
                .filter(filter)
                .sorted(Comparator.comparing(Supplier::getName))
                .map(FakeSupplierRepository::copy)
                .forEach(result::add);
        return result;
    }

    private static String key(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }

    private static Supplier copy(Supplier supplier) {
        return new Supplier(supplier.getId(), supplier.getName(), supplier.getPhone(),
                supplier.getEmail(), supplier.getAddress(), supplier.isActive());
    }
}
