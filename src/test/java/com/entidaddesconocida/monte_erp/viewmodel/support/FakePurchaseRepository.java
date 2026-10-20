package com.entidaddesconocida.monte_erp.viewmodel.support;

import com.entidaddesconocida.monte_erp.domain.model.Product;
import com.entidaddesconocida.monte_erp.domain.model.Purchase;
import com.entidaddesconocida.monte_erp.domain.model.Stock;
import com.entidaddesconocida.monte_erp.domain.model.Supplier;
import com.entidaddesconocida.monte_erp.domain.model.User;
import com.entidaddesconocida.monte_erp.domain.repository.PurchaseRepository;

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
 * In-memory double of {@link PurchaseRepository} for unit tests (RF-14).
 *
 * <p>Purchases are configured with {@link #withPurchase(Purchase)} and their lots
 * with {@link #withLot(int, Stock)}; {@link #throwOnAccess()} makes every method
 * throw {@link SQLException} and {@link #failOn(String)} fails only one. The
 * double stores <em>copies</em> so a bug in the ViewModel cannot mutate the
 * fixtures, and it mimics the database: {@link #insert(Purchase)} assigns the
 * generated id and {@link #findAll()} returns the purchases most recent first.</p>
 */
public class FakePurchaseRepository implements PurchaseRepository {

    private final Map<Integer, Purchase> purchases = new LinkedHashMap<>();
    private final Map<Integer, List<Stock>> lotsByPurchase = new LinkedHashMap<>();
    private final Set<String> failingMethods = new HashSet<>();
    private boolean throwOnAccess;
    private int nextId = 1;
    private Purchase lastInserted;

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

    /** The last purchase passed to {@link #insert(Purchase)}, for the assertions. */
    public Purchase lastInserted() {
        return lastInserted;
    }

    private void maybeThrow(String method) throws SQLException {
        if (throwOnAccess || failingMethods.contains(method)) {
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
    public List<Purchase> findAll() throws SQLException {
        maybeThrow("findAll");
        List<Purchase> result = new ArrayList<>();
        purchases.values().stream()
                .sorted(Comparator.comparing(Purchase::getPurchaseDate).reversed()
                        .thenComparing(Comparator.comparingInt(Purchase::getId).reversed()))
                .forEach(purchase -> {
                    Purchase copy = copy(purchase);
                    copy.setLots(copyLots(lotsByPurchase.getOrDefault(purchase.getId(), List.of())));
                    result.add(copy);
                });
        return result;
    }

    @Override
    public List<Stock> findLots(int purchaseId) throws SQLException {
        maybeThrow("findLots");
        return copyLots(lotsByPurchase.getOrDefault(purchaseId, List.of()));
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
                supplier.getEmail(), supplier.isActive());
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
