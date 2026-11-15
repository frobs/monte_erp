package com.entidaddesconocida.monte_erp.viewmodel.support;

import com.entidaddesconocida.monte_erp.domain.model.OrderStatus;
import com.entidaddesconocida.monte_erp.domain.model.Customer;
import com.entidaddesconocida.monte_erp.domain.model.Product;
import com.entidaddesconocida.monte_erp.domain.model.Purchase;
import com.entidaddesconocida.monte_erp.domain.model.ReportPeriod;
import com.entidaddesconocida.monte_erp.domain.model.SalesOrder;
import com.entidaddesconocida.monte_erp.domain.model.SalesOrderFilter;
import com.entidaddesconocida.monte_erp.domain.model.SalesOrderLine;
import com.entidaddesconocida.monte_erp.domain.model.SalesReportLine;
import com.entidaddesconocida.monte_erp.domain.model.Stock;
import com.entidaddesconocida.monte_erp.domain.model.StockIssue;
import com.entidaddesconocida.monte_erp.domain.model.User;
import com.entidaddesconocida.monte_erp.domain.repository.SalesOrderRepository;
import com.entidaddesconocida.monte_erp.domain.service.InsufficientStockException;

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
 * assigns the generated id, {@link #findAll(SalesOrderFilter)} returns the orders
 * most recent first, filtering in memory exactly like the SQL guards of the real
 * adapter, and {@link #changeStatus(int, String, String)} applies the same
 * optimistic guard as the {@code UPDATE ... WHERE id = ? AND status = ?}: it
 * returns 1 only when the stored status still matches {@code currentStatus}. It
 * also records the last filter and the number of queries, so the tests can
 * prove the ViewModel filters <strong>in the query</strong>.</p>
 */
public class FakeSalesOrderRepository implements SalesOrderRepository {

    private final Map<Integer, SalesOrder> orders = new LinkedHashMap<>();
    private final Map<Integer, List<SalesOrderLine>> linesByOrder = new LinkedHashMap<>();
    private final Map<Integer, List<StockIssue>> issuesByOrder = new LinkedHashMap<>();
    private final Set<String> failingMethods = new HashSet<>();
    private final Set<String> failingNextCalls = new HashSet<>();
    private boolean throwOnAccess;
    private InsufficientStockException insufficientStock;
    private int nextId = 1;
    private SalesOrderFilter lastFilter = SalesOrderFilter.all();
    private int findAllCalls;

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

    /** Seeds a consumed lot of a stored order, for the RF-15 trace tests. */
    public FakeSalesOrderRepository withIssue(int salesOrderId, StockIssue issue) {
        issuesByOrder.computeIfAbsent(salesOrderId, key -> new ArrayList<>()).add(copy(issue));
        return this;
    }

    /**
     * Makes the next {@link #insert(SalesOrder)} fail as if the repository had
     * re-read the lots and they no longer cover a line (RF-09, decision D4).
     */
    public FakeSalesOrderRepository failOnInsufficientStock(String productName, int requested,
                                                            int available) {
        this.insufficientStock = new InsufficientStockException(productName, requested, available);
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

    /** Fails only the next call to {@code method}, then resumes normally. */
    public FakeSalesOrderRepository failNext(String method) {
        failingNextCalls.add(method);
        return this;
    }

    /** Filter of the last {@code findAll} query, for the assertions. */
    public SalesOrderFilter lastFilter() {
        return lastFilter;
    }

    /** Number of {@code findAll} queries issued so far. */
    public int findAllCalls() {
        return findAllCalls;
    }

    private void maybeThrow(String method) throws SQLException {
        if (throwOnAccess || failingMethods.contains(method) || failingNextCalls.remove(method)) {
            throw new SQLException("Simulated database failure in " + method);
        }
    }

    @Override
    public int insert(SalesOrder order) throws SQLException, InsufficientStockException {
        maybeThrow("insert");
        if (insufficientStock != null) {
            throw insufficientStock;
        }
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
    public List<SalesOrder> findAll(SalesOrderFilter filter) throws SQLException {
        maybeThrow("findAll");
        lastFilter = filter;
        findAllCalls++;
        List<SalesOrder> result = new ArrayList<>();
        orders.values().stream()
                .filter(order -> matches(order, filter))
                .sorted(Comparator.comparing(SalesOrder::getOrderDate).reversed()
                        .thenComparing(Comparator.comparingInt(SalesOrder::getId).reversed()))
                .forEach(order -> {
                    SalesOrder copy = copy(order);
                    copy.setLines(copyLines(linesByOrder.getOrDefault(order.getId(), List.of())));
                    result.add(copy);
                });
        return result;
    }

    /**
     * Mirrors the SQL guards: an absent criterion does not filter, the date range
     * compares by day with both bounds inclusive, and a customer without an id
     * can never match a customer filter.
     */
    private static boolean matches(SalesOrder order, SalesOrderFilter filter) {
        if (!filter.period().isEmpty()
                && !filter.period().contains(order.getOrderDate())) {
            return false;
        }
        if (filter.customerId() != null && (order.getCustomer() == null
                || order.getCustomer().getId() != filter.customerId())) {
            return false;
        }
        return filter.status() == null || filter.status().equals(order.getStatus());
    }

    @Override
    public List<SalesOrderLine> findLines(int salesOrderId) throws SQLException {
        maybeThrow("findLines");
        return copyLines(linesByOrder.getOrDefault(salesOrderId, List.of()));
    }

    @Override
    public List<StockIssue> findIssues(int salesOrderId) throws SQLException {
        maybeThrow("findIssues");
        List<StockIssue> result = new ArrayList<>();
        for (StockIssue issue : issuesByOrder.getOrDefault(salesOrderId, List.of())) {
            result.add(copy(issue));
        }
        return result;
    }

    @Override
    public List<SalesReportLine> findReportLines(ReportPeriod period) throws SQLException {
        maybeThrow("findReportLines");
        List<SalesReportLine> lines = new ArrayList<>();
        for (SalesOrder order : orders.values()) {
            if (period != null && !period.contains(order.getOrderDate())) {
                continue;
            }
            for (SalesOrderLine line : linesByOrder.getOrDefault(order.getId(), List.of())) {
                lines.add(new SalesReportLine(order.getId(), order.getOrderDate(), order.getStatus(),
                        line.getQuantity(), line.getUnitPrice(), copy(line.getProduct())));
            }
        }
        // Mimics the database ORDER BY: product name, then order_date, then id.
        lines.sort(Comparator
                .comparing((SalesReportLine line) -> line.getProduct() == null
                        ? "" : line.getProduct().getName())
                .thenComparing(SalesReportLine::getOrderDate,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingInt(SalesReportLine::getOrderId));
        return lines;
    }

    @Override
    public int changeStatus(int id, OrderStatus currentStatus, OrderStatus newStatus)
            throws SQLException {
        maybeThrow("changeStatus");
        SalesOrder stored = orders.get(id);
        if (stored == null || currentStatus != stored.getStatus()) {
            return 0;
        }
        // A fresh copy with the full constructor (there is no public setStatus).
        orders.put(id, new SalesOrder(stored.getId(), stored.getOrderDate(), newStatus,
                stored.getTotal(), copy(stored.getCustomer()), copy(stored.getUser()),
                copyLines(linesByOrder.getOrDefault(id, List.of()))));
        if (newStatus == OrderStatus.CANCELLED) {
            // Cancelling gives the units back: the issues of the order disappear
            // (the counterpart of the reversion in the real repository, D2).
            issuesByOrder.remove(id);
        }
        return 1;
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

    private static StockIssue copy(StockIssue issue) {
        return new StockIssue(issue.getId(), issue.getQuantity(), copy(issue.getSalesOrderLine()),
                copy(issue.getStock()));
    }

    private static Stock copy(Stock stock) {
        if (stock == null) {
            return null;
        }
        Stock copy = new Stock(stock.getId(), stock.getInitialQuantity(),
                stock.getAvailableQuantity(), stock.getPurchasePrice(), stock.getEntryDate(),
                copy(stock.getProduct()), copy(stock.getPurchase()));
        return copy;
    }

    private static Purchase copy(Purchase purchase) {
        if (purchase == null) {
            return null;
        }
        return new Purchase(purchase.getId(), purchase.getPurchaseDate(), purchase.getStatus(),
                purchase.getTotal(), null, null, null);
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
