package com.entidaddesconocida.monte_erp.viewmodel.support;

import com.entidaddesconocida.monte_erp.domain.model.Customer;
import com.entidaddesconocida.monte_erp.domain.model.Product;
import com.entidaddesconocida.monte_erp.domain.model.SalesOrder;
import com.entidaddesconocida.monte_erp.domain.model.SalesOrderLine;
import com.entidaddesconocida.monte_erp.domain.model.User;
import com.entidaddesconocida.monte_erp.domain.repository.SalesOrderRepository;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * In-memory double of {@link SalesOrderRepository} for unit tests.
 *
 * <p>Orders are configured with {@link #withOrder(SalesOrder)}; {@link #throwOnAccess()}
 * makes every method throw {@link SQLException} and {@link #failOn(String)} fails
 * only one. The double stores <em>copies</em> so a bug in the ViewModel cannot
 * mutate the fixtures, and it mimics the database: {@link #insert(SalesOrder)}
 * assigns the generated id and {@link #findAll()} returns the orders most recent
 * first.</p>
 */
public class FakeSalesOrderRepository implements SalesOrderRepository {

    private final Map<Integer, SalesOrder> orders = new LinkedHashMap<>();
    private final Map<Integer, List<SalesOrderLine>> linesByOrder = new LinkedHashMap<>();
    private final Set<String> failingMethods = new HashSet<>();
    private boolean throwOnAccess;
    private int nextId = 1;

    /** Stores a pre-existing order (and its lines) as if it came from the database. */
    public FakeSalesOrderRepository withOrder(SalesOrder order) {
        if (order.getId() == 0) {
            order.setId(nextId++);
        }
        if (order.getOrderDate() == null) {
            order.setOrderDate(LocalDateTime.now());
        }
        orders.put(order.getId(), copy(order));
        linesByOrder.put(order.getId(), copyLines(order.getLines()));
        return this;
    }

    /** Adds a line to a stored order, for the detail tests. */
    public FakeSalesOrderRepository withLine(int salesOrderId, SalesOrderLine line) {
        linesByOrder.computeIfAbsent(salesOrderId, key -> new ArrayList<>()).add(copy(line));
        return this;
    }

    public FakeSalesOrderRepository throwOnAccess() {
        this.throwOnAccess = true;
        return this;
    }

    /** Makes every future call to {@code method} fail, keeping the rest usable. */
    public FakeSalesOrderRepository failOn(String method) {
        failingMethods.add(method);
        return this;
    }

    private void maybeThrow(String method) throws SQLException {
        if (throwOnAccess || failingMethods.contains(method)) {
            throw new SQLException("Simulated database failure in " + method);
        }
    }

    @Override
    public int insert(SalesOrder order) throws SQLException {
        maybeThrow("insert");
        int id = nextId++;
        order.setId(id);
        if (order.getOrderDate() == null) {
            order.setOrderDate(LocalDateTime.now());
        }
        orders.put(id, copy(order));
        linesByOrder.put(id, copyLines(order.getLines()));
        return id;
    }

    @Override
    public List<SalesOrder> findAll() throws SQLException {
        maybeThrow("findAll");
        List<SalesOrder> result = new ArrayList<>();
        orders.values().stream()
                .sorted(Comparator.comparing(SalesOrder::getOrderDate).reversed()
                        .thenComparing(Comparator.comparingInt(SalesOrder::getId).reversed()))
                .forEach(order -> {
                    SalesOrder copy = copy(order);
                    copy.setLines(copyLines(linesByOrder.getOrDefault(order.getId(), List.of())));
                    result.add(copy);
                });
        return result;
    }

    @Override
    public List<SalesOrderLine> findLines(int salesOrderId) throws SQLException {
        maybeThrow("findLines");
        return copyLines(linesByOrder.getOrDefault(salesOrderId, List.of()));
    }

    private static SalesOrder copy(SalesOrder order) {
        List<SalesOrderLine> lines = copyLines(order.getLines());
        SalesOrder copy = new SalesOrder(order.getId(), order.getOrderDate(),
                order.getStatus(), order.getTotal(), copy(order.getCustomer()),
                copy(order.getUser()), lines);
        return copy;
    }

    private static List<SalesOrderLine> copyLines(List<SalesOrderLine> lines) {
        List<SalesOrderLine> copies = new ArrayList<>();
        for (SalesOrderLine line : lines) {
            copies.add(copy(line));
        }
        return copies;
    }

    private static SalesOrderLine copy(SalesOrderLine line) {
        return new SalesOrderLine(line.getId(), line.getQuantity(), line.getUnitPrice(),
                copy(line.getProduct()));
    }

    private static Product copy(Product product) {
        if (product == null) {
            return null;
        }
        Product copy = new Product(product.getName(), product.getSku(), product.getDescription(),
                product.getPrice(), product.getMinStock(), product.getCategory(),
                product.isActive());
        copy.setId(product.getId());
        return copy;
    }

    private static Customer copy(Customer customer) {
        if (customer == null) {
            return null;
        }
        return new Customer(customer.getId(), customer.getName(), customer.getPhone(),
                customer.getEmail(), customer.getAddress(), customer.isActive());
    }

    private static User copy(User user) {
        if (user == null) {
            return null;
        }
        User copy = new User(user.getName(), user.getUsername(), user.getPasswordHash(),
                user.getRole(), user.isActive());
        copy.setId(user.getId());
        return copy;
    }
}
