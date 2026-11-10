package com.entidaddesconocida.monte_erp.viewmodel.support;

import com.entidaddesconocida.monte_erp.domain.model.Product;
import com.entidaddesconocida.monte_erp.domain.model.Purchase;
import com.entidaddesconocida.monte_erp.domain.model.ReportPeriod;
import com.entidaddesconocida.monte_erp.domain.model.Stock;
import com.entidaddesconocida.monte_erp.domain.model.StockLevel;
import com.entidaddesconocida.monte_erp.domain.repository.StockRepository;
import com.entidaddesconocida.monte_erp.viewmodel.SearchText;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * In-memory double of {@link StockRepository} for unit tests (RF-09/RF-10).
 *
 * <p>Levels are configured with {@link #withLevel(StockLevel)} and lots with
 * {@link #withLot(int, Stock)}; {@link #throwOnAccess()} makes every method throw
 * {@link SQLException} and {@link #failOn(String)} fails only one. The double
 * stores <em>copies</em> so a bug in the ViewModel cannot mutate the fixtures, and
 * it mimics the database: {@link #findLevels()} is ordered by name,
 * {@link #findLots(int)} returns every lot oldest first and
 * {@link #findAvailable(int)} filters out the lots at 0.</p>
 */
public class FakeStockRepository implements StockRepository {

    private static final Comparator<Stock> FIFO_ORDER =
            Comparator.comparing(Stock::getEntryDate,
                            Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparingInt(Stock::getId);

    private final Map<Integer, StockLevel> levels = new LinkedHashMap<>();
    private final Map<Integer, List<Stock>> lotsByProduct = new LinkedHashMap<>();
    private final Set<String> failingMethods = new HashSet<>();
    private boolean throwOnAccess;

    /** Stores the level of a product (overwrites the previous one). */
    public FakeStockRepository withLevel(StockLevel level) {
        levels.put(level.getProduct().getId(), copy(level));
        return this;
    }

    /** Stores a lot of a product, as if it came from the database. */
    public FakeStockRepository withLot(int productId, Stock lot) {
        lotsByProduct.computeIfAbsent(productId, key -> new ArrayList<>()).add(copy(lot));
        return this;
    }

    public FakeStockRepository throwOnAccess() {
        this.throwOnAccess = true;
        return this;
    }

    /** Makes every future call to {@code method} fail, keeping the rest usable. */
    public FakeStockRepository failOn(String method) {
        failingMethods.add(method);
        return this;
    }

    private void maybeThrow(String method) throws SQLException {
        if (throwOnAccess || failingMethods.contains(method)) {
            throw new SQLException("Simulated database failure in " + method);
        }
    }

    @Override
    public List<StockLevel> findLevels() throws SQLException {
        maybeThrow("findLevels");
        return orderedLevels(level -> true);
    }

    @Override
    public List<StockLevel> findLevels(String search, boolean onlyBelowMinimum)
            throws SQLException {
        maybeThrow("findLevels");
        return orderedLevels(level -> matches(level, search, onlyBelowMinimum));
    }

    @Override
    public int countBelowMinimum() throws SQLException {
        maybeThrow("countBelowMinimum");
        return (int) levels.values().stream()
                .filter(StockLevel::isBelowMinimum)
                .count();
    }

    @Override
    public List<Stock> findLots(int productId) throws SQLException {
        maybeThrow("findLots");
        return orderedLots(productId, false);
    }

    @Override
    public List<Stock> findAvailable(int productId) throws SQLException {
        maybeThrow("findAvailable");
        return orderedLots(productId, true);
    }

    @Override
    public List<Stock> findAllLots(ReportPeriod period) throws SQLException {
        maybeThrow("findAllLots");
        List<Stock> result = new ArrayList<>();
        for (List<Stock> lots : lotsByProduct.values()) {
            for (Stock lot : lots) {
                if (period == null || period.contains(lot.getEntryDate())) {
                    result.add(copy(lot));
                }
            }
        }
        // Mimics the database ORDER BY: product name, then entry_date, then id.
        result.sort(Comparator
                .comparing((Stock lot) -> lot.getProduct() == null ? "" : lot.getProduct().getName())
                .thenComparing(Stock::getEntryDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingInt(Stock::getId));
        return result;
    }

    private List<StockLevel> orderedLevels(java.util.function.Predicate<StockLevel> filter) {
        List<StockLevel> result = new ArrayList<>();
        levels.values().stream()
                .filter(filter)
                .map(FakeStockRepository::copy)
                .sorted(Comparator.comparing(level -> level.getProduct().getName()))
                .forEach(result::add);
        return result;
    }

    private static boolean matches(StockLevel level, String search, boolean onlyBelowMinimum) {
        if (level.getProduct() == null) {
            return false;
        }
        if (onlyBelowMinimum && !level.isBelowMinimum()) {
            return false;
        }
        return SearchText.matches(search, level.getProduct().getName(), level.getProduct().getSku());
    }

    private List<Stock> orderedLots(int productId, boolean onlyAvailable) {
        List<Stock> result = new ArrayList<>();
        lotsByProduct.getOrDefault(productId, List.of()).stream()
                .filter(lot -> !onlyAvailable || lot.getAvailableQuantity() > 0)
                .sorted(FIFO_ORDER)
                .map(FakeStockRepository::copy)
                .forEach(result::add);
        return result;
    }

    private static StockLevel copy(StockLevel level) {
        return new StockLevel(copy(level.getProduct()), level.getAvailableQuantity());
    }

    private static Stock copy(Stock stock) {
        return new Stock(stock.getId(), stock.getInitialQuantity(), stock.getAvailableQuantity(),
                stock.getPurchasePrice(), stock.getEntryDate(), copy(stock.getProduct()),
                copy(stock.getPurchase()));
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
}
