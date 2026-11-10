package com.entidaddesconocida.monte_erp.viewmodel.support;

import com.entidaddesconocida.monte_erp.domain.model.Customer;
import com.entidaddesconocida.monte_erp.domain.repository.CustomerRepository;
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
 * In-memory double of {@link CustomerRepository} for unit tests.
 *
 * <p>Los clientes se configuran directamente con {@link #withCustomer(Customer)};
 * {@link #throwOnAccess()} hace que cualquier método lance {@link SQLException}
 * y {@link #failOn(String)} permite fallar solo ese método. El doble guarda
 * <em>copias</em> de las filas, ordena por nombre y trata el nombre como
 * case-insensitive, igual que la colación {@code utf8mb4_unicode_ci} de la tabla
 * real. {@link #insert(Customer)} simula la clave única {@code uq_customer_name}.</p>
 */
public class FakeCustomerRepository implements CustomerRepository {

    private final Map<Integer, Customer> customers = new LinkedHashMap<>();
    private final Set<String> failingMethods = new HashSet<>();
    private boolean throwOnAccess;
    private int nextId = 1;

    public FakeCustomerRepository withCustomer(Customer customer) {
        if (customer.getId() == 0) {
            customer.setId(nextId++);
        }
        customers.put(customer.getId(), copy(customer));
        return this;
    }

    public FakeCustomerRepository throwOnAccess() {
        this.throwOnAccess = true;
        return this;
    }

    /** Makes every future call to {@code method} fail, keeping the rest usable. */
    public FakeCustomerRepository failOn(String method) {
        failingMethods.add(method);
        return this;
    }

    private void maybeThrow(String method) throws SQLException {
        if (throwOnAccess || failingMethods.contains(method)) {
            throw new SQLException("Simulated database failure in " + method);
        }
    }

    @Override
    public List<Customer> findAll() throws SQLException {
        maybeThrow("findAll");
        return ordered(customer -> true);
    }

    @Override
    public List<Customer> findActive() throws SQLException {
        maybeThrow("findActive");
        return ordered(Customer::isActive);
    }

    @Override
    public List<Customer> findActive(String search) throws SQLException {
        maybeThrow("findActive");
        return ordered(customer -> customer.isActive() && matches(customer, search));
    }

    @Override
    public List<Customer> findInactive() throws SQLException {
        maybeThrow("findInactive");
        return ordered(customer -> !customer.isActive());
    }

    @Override
    public List<Customer> findInactive(String search) throws SQLException {
        maybeThrow("findInactive");
        return ordered(customer -> !customer.isActive() && matches(customer, search));
    }

    @Override
    public Customer findByName(String name) throws SQLException {
        maybeThrow("findByName");
        return customers.values().stream()
                .filter(customer -> key(customer.getName()).equals(key(name)))
                .findFirst()
                .map(FakeCustomerRepository::copy)
                .orElse(null);
    }

    @Override
    public int insert(Customer customer) throws SQLException {
        maybeThrow("insert");
        if (findByName(customer.getName()) != null) {
            throw new SQLIntegrityConstraintViolationException(
                    "Duplicate entry for key 'uq_customer_name': " + customer.getName());
        }
        customer.setId(nextId++);
        customers.put(customer.getId(), copy(customer));
        return customer.getId();
    }

    @Override
    public void update(Customer customer) throws SQLException {
        maybeThrow("update");
        Customer clash = findByName(customer.getName());
        if (clash != null && clash.getId() != customer.getId()) {
            throw new SQLIntegrityConstraintViolationException(
                    "Duplicate entry for key 'uq_customer_name': " + customer.getName());
        }
        customers.put(customer.getId(), copy(customer));
    }

    @Override
    public void deactivate(int id) throws SQLException {
        maybeThrow("deactivate");
        Customer stored = customers.get(id);
        if (stored != null) {
            stored.setActive(false);
        }
    }

    @Override
    public void activate(int id) throws SQLException {
        maybeThrow("activate");
        Customer stored = customers.get(id);
        if (stored != null) {
            stored.setActive(true);
        }
    }

    private List<Customer> ordered(java.util.function.Predicate<Customer> filter) {
        List<Customer> result = new ArrayList<>();
        customers.values().stream()
                .filter(filter)
                .sorted(Comparator.comparing(Customer::getName))
                .map(FakeCustomerRepository::copy)
                .forEach(result::add);
        return result;
    }

    private static String key(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }

    private static boolean matches(Customer customer, String search) {
        return SearchText.matches(search, customer.getName(), customer.getPhone(),
                customer.getEmail());
    }

    private static Customer copy(Customer customer) {
        return new Customer(customer.getId(), customer.getName(), customer.getPhone(),
                customer.getEmail(), customer.getAddress(), customer.isActive());
    }
}
