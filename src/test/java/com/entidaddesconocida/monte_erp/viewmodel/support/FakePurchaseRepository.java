package com.entidaddesconocida.monte_erp.viewmodel.support;

import com.entidaddesconocida.monte_erp.domain.model.PurchaseStatus;
import com.entidaddesconocida.monte_erp.domain.model.Product;
import com.entidaddesconocida.monte_erp.domain.model.ProductSupplier;
import com.entidaddesconocida.monte_erp.domain.model.Purchase;
import com.entidaddesconocida.monte_erp.domain.model.PurchaseFilter;
import com.entidaddesconocida.monte_erp.domain.model.Stock;
import com.entidaddesconocida.monte_erp.domain.model.Supplier;
import com.entidaddesconocida.monte_erp.domain.model.User;
import com.entidaddesconocida.monte_erp.domain.repository.PurchaseRepository;
import com.entidaddesconocida.monte_erp.domain.service.PurchaseStockConsumedException;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * In-memory double of {@link PurchaseRepository} for unit tests (RF-14).
 *
 * <p>Purchases are configured with {@link #withPurchase(Purchase)} and their lots
 * with {@link #withLot(int, Stock)}; {@link #throwOnAccess()} makes every method
 * throw {@link SQLException} and {@link #failOn(String)} fails only one. The
 * double stores <em>copies</em> so a bug in the ViewModel cannot mutate the
 * fixtures, and it mimics the database: {@link #insert(Purchase)} assigns the
 * generated id and {@link #findAll(PurchaseFilter)} returns the purchases most
 * recent first, filtering in memory exactly like the SQL guards of the real
 * adapter. It also records the last filter and the number of queries, so the
 * tests can prove the ViewModel filters <strong>in the query</strong>.</p>
 */
public class FakePurchaseRepository implements PurchaseRepository {

    private final Map<Integer, Purchase> purchases = new LinkedHashMap<>();
    private final Map<Integer, List<Stock>> lotsByPurchase = new LinkedHashMap<>();
    private final Set<String> failingMethods = new HashSet<>();
    private final Set<String> failingNextCalls = new HashSet<>();
    private boolean throwOnAccess;
    private int nextId = 1;
    private Purchase lastInserted;
    private PurchaseFilter lastFilter = PurchaseFilter.all();
    private int findAllCalls;

    /** Stores a pre-existing purchase (and its lots) as if it came from the database. */
    public FakePurchaseRepository withPurchase(Purchase purchase) {
        if (purchase.getId() == 0) {
            purchase.setId(nextId++);
        }
        if (purchase.getPurchaseDate() == null) {
            purchase.setPurchaseDate(LocalDateTime.now());
        }
        purchases.put(purchase.getId(), copy(purchase));
        lotsByPurchase.put(purchase.getId(), copyLots(purchase.getLots()));
        return this;
    }

    /** Adds a lot to a stored purchase, for the detail tests. */
    public FakePurchaseRepository withLot(int purchaseId, Stock lot) {
        lotsByPurchase.computeIfAbsent(purchaseId, key -> new ArrayList<>()).add(copy(lot));
        return this;
    }

    public FakePurchaseRepository throwOnAccess() {
        this.throwOnAccess = true;
        return this;
    }

    /** Makes every future call to {@code method} fail, keeping the rest usable. */
    public FakePurchaseRepository failOn(String method) {
        failingMethods.add(method);
        return this;
    }

    /** Fails only the next call to {@code method}, then resumes normally. */
    public FakePurchaseRepository failNext(String method) {
        failingNextCalls.add(method);
        return this;
    }

    /** The last purchase passed to {@link #insert(Purchase)}, for the assertions. */
    public Purchase lastInserted() {
        return lastInserted;
    }

    /** Filter of the last {@code findAll} query, for the assertions. */
    public PurchaseFilter lastFilter() {
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
    public int insert(Purchase purchase) throws SQLException {
        maybeThrow("insert");
        int id = nextId++;
        purchase.setId(id);
        if (purchase.getPurchaseDate() == null) {
            purchase.setPurchaseDate(LocalDateTime.now());
        }
        lastInserted = copy(purchase);
        purchases.put(id, copy(purchase));
        lotsByPurchase.put(id, copyLots(purchase.getLots()));
        return id;
    }

    @Override
    public List<Purchase> findAll(PurchaseFilter filter) throws SQLException {
        maybeThrow("findAll");
        lastFilter = filter;
        findAllCalls++;
        List<Purchase> result = new ArrayList<>();
        purchases.values().stream()
                .filter(purchase -> matches(purchase, filter))
                .sorted(Comparator.comparing(Purchase::getPurchaseDate).reversed()
                        .thenComparing(Comparator.comparingInt(Purchase::getId).reversed()))
                .forEach(purchase -> {
                    Purchase copy = copy(purchase);
                    copy.setLots(copyLots(lotsByPurchase.getOrDefault(purchase.getId(), List.of())));
                    result.add(copy);
                });
        return result;
    }

    /**
     * Mirrors the SQL guards: an absent criterion does not filter, the date range
     * compares by day with both bounds inclusive, and a supplier without an id
     * can never match a supplier filter.
     */
    private static boolean matches(Purchase purchase, PurchaseFilter filter) {
        if (!filter.period().isEmpty()
                && !filter.period().contains(purchase.getPurchaseDate())) {
            return false;
        }
        if (filter.supplierId() != null && (purchase.getSupplier() == null
                || purchase.getSupplier().getId() != filter.supplierId())) {
            return false;
        }
        return filter.status() == null || filter.status().equals(purchase.getStatus());
    }

    @Override
    public List<Stock> findLots(int purchaseId) throws SQLException {
        maybeThrow("findLots");
        return copyLots(lotsByPurchase.getOrDefault(purchaseId, List.of()));
    }

    /**
     * Applies the status change with the same rules as the real adapter: the
     * optimistic guard returns 0 on mismatch, receiving makes every lot
     * available and returning empties them only when no unit was sold.
     */
    @Override
    public int changeStatus(int id, PurchaseStatus currentStatus, PurchaseStatus newStatus)
            throws SQLException, PurchaseStockConsumedException {
        maybeThrow("changeStatus");
        Purchase stored = purchases.get(id);
        if (stored == null || currentStatus != stored.getStatus()) {
            return 0;
        }
        List<Stock> lots = lotsByPurchase.getOrDefault(id, List.of());
        if (newStatus == PurchaseStatus.RETURNED) {
            int consumed = lots.stream()
                    .mapToInt(lot -> lot.getInitialQuantity() - lot.getAvailableQuantity())
                    .sum();
            if (consumed > 0) {
                throw new PurchaseStockConsumedException(id, consumed);
            }
        }
        for (Stock lot : lots) {
            if (newStatus == PurchaseStatus.RECEIVED) {
                lot.setAvailableQuantity(lot.getInitialQuantity());
            } else if (newStatus == PurchaseStatus.RETURNED) {
                lot.setAvailableQuantity(0);
            }
        }
        // A fresh copy with the full constructor (there is no public setStatus).
        purchases.put(id, new Purchase(stored.getId(), stored.getPurchaseDate(), newStatus,
                stored.getTotal(), copy(stored.getSupplier()), copy(stored.getUser()),
                copyLots(lots)));
        return 1;
    }

    /**
     * Derives the product ↔ supplier pairs from the stored purchases and their
     * lots, like the SQL {@code SELECT DISTINCT s.product_id, p.supplier_id} of
     * the real adapter: it does not look at {@code available_quantity} (a lot at
     * 0 still counts, decision D3) and returns one pair per product/supplier.
     */
    @Override
    public List<ProductSupplier> findProductSuppliers() throws SQLException {
        maybeThrow("findProductSuppliers");
        Set<ProductSupplier> pairs = new TreeSet<>(
                Comparator.comparingInt(ProductSupplier::getProductId)
                        .thenComparingInt(ProductSupplier::getSupplierId));
        for (Purchase purchase : purchases.values()) {
            if (purchase.getSupplier() == null) {
                continue;
            }
            int supplierId = purchase.getSupplier().getId();
            for (Stock lot : lotsByPurchase.getOrDefault(purchase.getId(), List.of())) {
                if (lot.getProduct() != null) {
                    pairs.add(new ProductSupplier(lot.getProduct().getId(), supplierId));
                }
            }
        }
        return new ArrayList<>(pairs);
    }

    private static Purchase copy(Purchase purchase) {
        Purchase copy = new Purchase(purchase.getId(), purchase.getPurchaseDate(),
                purchase.getStatus(), purchase.getTotal(), copy(purchase.getSupplier()),
                copy(purchase.getUser()), copyLots(purchase.getLots()));
        return copy;
    }

    private static List<Stock> copyLots(List<Stock> lots) {
        List<Stock> copies = new ArrayList<>();
        for (Stock lot : lots) {
            copies.add(copy(lot));
        }
        return copies;
    }

    private static Stock copy(Stock lot) {
        return new Stock(lot.getId(), lot.getInitialQuantity(), lot.getAvailableQuantity(),
                lot.getPurchasePrice(), lot.getEntryDate(), copy(lot.getProduct()), null);
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

    private static Supplier copy(Supplier supplier) {
        if (supplier == null) {
            return null;
        }
        return new Supplier(supplier.getId(), supplier.getName(), supplier.getPhone(),
                supplier.getEmail(), supplier.getAddress(), supplier.isActive());
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
