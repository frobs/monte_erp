package com.entidaddesconocida.monte_erp.viewmodel.support;

import com.entidaddesconocida.monte_erp.domain.model.Product;
import com.entidaddesconocida.monte_erp.domain.model.ProductFilter;
import com.entidaddesconocida.monte_erp.domain.repository.ProductRepository;
import com.entidaddesconocida.monte_erp.viewmodel.SearchText;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * In-memory double of {@link ProductRepository} for unit tests.
 *
 * <p>Productos se configuran con {@link #withProduct(Product)};
 * {@link #throwOnAccess()} hace que cualquier método lance {@link SQLException} y
 * {@link #failOn(String)} permite fallar solo uno (p. ej. {@code "update"}). El
 * doble guarda <em>copias</em> de las filas, ordena por nombre y trata el SKU
 * como case-insensitive, igual que la colación {@code utf8mb4_unicode_ci} de la
 * tabla real.</p>
 */
public class FakeProductRepository implements ProductRepository {

    private final Map<Integer, Product> products = new LinkedHashMap<>();
    private final Map<Integer, Set<Integer>> supplierIdsByProduct = new HashMap<>();
    private final Set<String> failingMethods = new HashSet<>();
    private boolean throwOnAccess;
    private int nextId = 1;

    public FakeProductRepository withProduct(Product product) {
        if (product.getId() == 0) {
            product.setId(nextId++);
        }
        products.put(product.getId(), copy(product));
        return this;
    }

    /**
     * Registers that the product has at least one lot of a purchase from the
     * supplier, which is what the real query resolves through
     * {@code STOCK JOIN PURCHASE}.
     */
    public FakeProductRepository withSupplier(int productId, int supplierId) {
        supplierIdsByProduct.computeIfAbsent(productId, key -> new HashSet<>()).add(supplierId);
        return this;
    }

    public FakeProductRepository throwOnAccess() {
        this.throwOnAccess = true;
        return this;
    }

    /** Makes every future call to {@code method} fail, keeping the rest usable. */
    public FakeProductRepository failOn(String method) {
        failingMethods.add(method);
        return this;
    }

    private void maybeThrow(String method) throws SQLException {
        if (throwOnAccess || failingMethods.contains(method)) {
            throw new SQLException("Simulated database failure in " + method);
        }
    }

    @Override
    public List<Product> findAll() throws SQLException {
        maybeThrow("findAll");
        return ordered(product -> product.isActive());
    }

    @Override
    public List<Product> findAll(ProductFilter filter) throws SQLException {
        maybeThrow("findAll");
        return ordered(product -> product.isActive() && matches(product, filter));
    }

    @Override
    public List<Product> findInactive() throws SQLException {
        maybeThrow("findInactive");
        return ordered(product -> !product.isActive());
    }

    @Override
    public List<Product> findInactive(ProductFilter filter) throws SQLException {
        maybeThrow("findInactive");
        return ordered(product -> !product.isActive() && matches(product, filter));
    }

    @Override
    public Product findBySku(String sku) throws SQLException {
        maybeThrow("findBySku");
        return products.values().stream()
                .filter(product -> key(product.getSku()).equals(key(sku)))
                .findFirst()
                .map(FakeProductRepository::copy)
                .orElse(null);
    }

    @Override
    public int insert(Product product) throws SQLException {
        maybeThrow("insert");
        if (findBySku(product.getSku()) != null) {
            throw new SQLIntegrityConstraintViolationException(
                    "Duplicate entry for key 'uq_product_sku': " + product.getSku());
        }
        product.setId(nextId++);
        products.put(product.getId(), copy(product));
        return product.getId();
    }

    @Override
    public void update(Product product) throws SQLException {
        maybeThrow("update");
        Product clash = findBySku(product.getSku());
        if (clash != null && clash.getId() != product.getId()) {
            throw new SQLIntegrityConstraintViolationException(
                    "Duplicate entry for key 'uq_product_sku': " + product.getSku());
        }
        products.put(product.getId(), copy(product));
    }

    @Override
    public void deactivate(int id) throws SQLException {
        maybeThrow("deactivate");
        Product stored = products.get(id);
        if (stored != null) {
            stored.setActive(false);
        }
    }

    @Override
    public void activate(int id) throws SQLException {
        maybeThrow("activate");
        Product stored = products.get(id);
        if (stored != null) {
            stored.setActive(true);
        }
    }

    private List<Product> ordered(java.util.function.Predicate<Product> filter) {
        List<Product> result = new ArrayList<>();
        products.values().stream()
                .filter(filter)
                .sorted(Comparator.comparing(Product::getName))
                .map(FakeProductRepository::copy)
                .forEach(result::add);
        return result;
    }

    /** Mirrors the SQL criteria: text over name/SKU, category id and supplier history. */
    private boolean matches(Product product, ProductFilter filter) {
        if (filter == null) {
            return true;
        }
        if (!SearchText.matches(filter.search(), product.getName(), product.getSku())) {
            return false;
        }
        if (filter.categoryId() != null && (product.getCategory() == null
                || product.getCategory().getId() != filter.categoryId())) {
            return false;
        }
        return filter.supplierId() == null || supplierIdsByProduct
                .getOrDefault(product.getId(), Set.of())
                .contains(filter.supplierId());
    }

    private static String key(String sku) {
        return sku == null ? "" : sku.toLowerCase(Locale.ROOT);
    }

    private static Product copy(Product product) {
        Product copy = new Product(product.getName(), product.getSku(), product.getDescription(),
                product.getPrice(), product.getMinStock(), product.getCategory(),
                product.isActive());
        copy.setId(product.getId());
        return copy;
    }
}
