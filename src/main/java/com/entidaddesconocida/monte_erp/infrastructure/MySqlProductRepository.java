package com.entidaddesconocida.monte_erp.infrastructure;

import com.entidaddesconocida.monte_erp.domain.model.Category;
import com.entidaddesconocida.monte_erp.domain.model.Product;
import com.entidaddesconocida.monte_erp.domain.model.ProductFilter;
import com.entidaddesconocida.monte_erp.domain.repository.ProductRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * JDBC implementation of {@link ProductRepository} for the {@code PRODUCT}
 * table.
 *
 * <p>No business logic: it only translates between {@link Product} and the table
 * rows. The queries <em>left join</em> {@code CATEGORY} so the entity carries the
 * name the table column displays, even though only its id is needed to write the
 * foreign key; the left join keeps products without a category (the field is
 * optional in RF-03). There is no join to {@code SUPPLIER}: the supplier is
 * related to the purchase, not to the product.</p>
 *
 * <p>Deleting a product is logical ({@link #deactivate(int)}), as required by
 * {@code ON DELETE RESTRICT} and the recorded purchase/sales history.</p>
 */
public class MySqlProductRepository implements ProductRepository {

    private final ConnectionFactory connections;

    public MySqlProductRepository(ConnectionFactory connections) {
        this.connections = Objects.requireNonNull(connections, "connections");
    }

    private static final String COLUMNS = """
            p.id, p.name, p.sku, p.description, p.price, p.min_stock, p.active,
            p.category_id, c.name AS category_name, c.active AS category_active
            """;
    // LEFT JOIN so a product without a category (RF-03 makes it optional) is
    // still listed, with a NULL category_name.
    private static final String SELECT_ALL = "SELECT " + COLUMNS + " FROM PRODUCT p"
            + " LEFT JOIN CATEGORY c ON p.category_id = c.id";
    /**
     * Same projection as {@link #SELECT_ALL} plus the screen filters. One
     * {@code ? IS NULL} guard per criterion keeps a single statement for every
     * combination; the supplier is resolved with the full purchase history
     * ({@code STOCK JOIN PURCHASE}), so a lot at 0 still counts (decision D3).
     */
    private static final String SELECT_FILTERED = SELECT_ALL
            + " WHERE p.active = ?"
            + " AND (? IS NULL OR p.name LIKE ? ESCAPE '!' OR p.sku LIKE ? ESCAPE '!')"
            + " AND (? IS NULL OR p.category_id = ?)"
            + " AND (? IS NULL OR EXISTS ("
            + " SELECT 1 FROM STOCK fs JOIN PURCHASE fp ON fp.id = fs.purchase_id"
            + " WHERE fs.product_id = p.id AND fp.supplier_id = ?))"
            + " ORDER BY p.name";
    private static final String INSERT_PRODUCT = """
            INSERT INTO PRODUCT (name, sku, description, price, min_stock, category_id, active)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String UPDATE_PRODUCT = """
            UPDATE PRODUCT
               SET name = ?, sku = ?, description = ?, price = ?, min_stock = ?,
                   category_id = ?, active = ?
             WHERE id = ?
            """;
    private static final String DEACTIVATE_PRODUCT = "UPDATE PRODUCT SET active = 0 WHERE id = ?";
    private static final String ACTIVATE_PRODUCT = "UPDATE PRODUCT SET active = 1 WHERE id = ?";

    @Override
    public List<Product> findAll() throws SQLException {
        return query(SELECT_ALL + " WHERE p.active = 1 ORDER BY p.name");
    }

    @Override
    public List<Product> findAll(ProductFilter filter) throws SQLException {
        if (filter == null || !filter.isFiltered()) {
            return findAll();
        }
        return queryFiltered(filter, true);
    }

    @Override
    public List<Product> findInactive() throws SQLException {
        return query(SELECT_ALL + " WHERE p.active = 0 ORDER BY p.name");
    }

    @Override
    public List<Product> findInactive(ProductFilter filter) throws SQLException {
        if (filter == null || !filter.isFiltered()) {
            return findInactive();
        }
        return queryFiltered(filter, false);
    }

    @Override
    public Product findBySku(String sku) throws SQLException {
        String sql = SELECT_ALL + " WHERE p.sku = ?";
        try (Connection connection = connections.open();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, sku);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    @Override
    public int insert(Product product) throws SQLException {
        try (Connection connection = connections.open();
             PreparedStatement ps = connection
                     .prepareStatement(INSERT_PRODUCT, Statement.RETURN_GENERATED_KEYS)) {
            bindCommon(ps, product);
            ps.setBoolean(7, product.isActive());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                int id = keys.next() ? keys.getInt(1) : 0;
                product.setId(id);
                return id;
            }
        }
    }

    @Override
    public void update(Product product) throws SQLException {
        try (Connection connection = connections.open();
             PreparedStatement ps = connection.prepareStatement(UPDATE_PRODUCT)) {
            bindCommon(ps, product);
            ps.setBoolean(7, product.isActive());
            ps.setInt(8, product.getId());
            ps.executeUpdate();
        }
    }

    @Override
    public void deactivate(int id) throws SQLException {
        setActive(DEACTIVATE_PRODUCT, id);
    }

    @Override
    public void activate(int id) throws SQLException {
        setActive(ACTIVATE_PRODUCT, id);
    }

    private void setActive(String sql, int id) throws SQLException {
        try (Connection connection = connections.open();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    /**
     * Binds the six editable columns shared by {@code INSERT} and
     * {@code UPDATE} (the active flag and the id are appended separately).
     * {@code description} and {@code min_stock} are written from the entity so an
     * edition from the RF-03 form never erases them.
     */
    private void bindCommon(PreparedStatement ps, Product product) throws SQLException {
        ps.setString(1, product.getName());
        ps.setString(2, product.getSku());
        ps.setString(3, product.getDescription());
        ps.setBigDecimal(4, product.getPrice());
        ps.setInt(5, product.getMinStock());
        if (product.getCategory() == null) {
            ps.setNull(6, Types.INTEGER);
        } else {
            ps.setInt(6, product.getCategory().getId());
        }
    }

    private List<Product> query(String sql) throws SQLException {
        List<Product> products = new ArrayList<>();
        try (Connection connection = connections.open();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                products.add(map(rs));
            }
        }
        return products;
    }

    private List<Product> queryFiltered(ProductFilter filter, boolean active) throws SQLException {
        List<Product> products = new ArrayList<>();
        String pattern = SqlSearch.likePattern(filter.search());
        try (Connection connection = connections.open();
             PreparedStatement ps = connection.prepareStatement(SELECT_FILTERED)) {
            ps.setBoolean(1, active);
            bindString(ps, 2, pattern);
            bindString(ps, 3, pattern);
            bindString(ps, 4, pattern);
            bindInt(ps, 5, filter.categoryId());
            bindInt(ps, 6, filter.categoryId());
            bindInt(ps, 7, filter.supplierId());
            bindInt(ps, 8, filter.supplierId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    products.add(map(rs));
                }
            }
        }
        return products;
    }

    private static void bindString(PreparedStatement ps, int index, String value)
            throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.VARCHAR);
        } else {
            ps.setString(index, value);
        }
    }

    private static void bindInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    private Product map(ResultSet rs) throws SQLException {
        Product product = new Product();
        product.setId(rs.getInt("id"));
        product.setName(rs.getString("name"));
        product.setSku(rs.getString("sku"));
        product.setDescription(rs.getString("description"));
        product.setPrice(rs.getBigDecimal("price"));
        product.setMinStock(rs.getInt("min_stock"));
        product.setActive(rs.getBoolean("active"));
        // Only id, name and active are needed: the combo/list render the name,
        // the foreign key is written from the id and the active flag tells the
        // product form whether the category can still be assigned (RF-04). A
        // missing category stays null instead of becoming Category(0, null).
        int categoryId = rs.getInt("category_id");
        if (!rs.wasNull()) {
            product.setCategory(new Category(categoryId, rs.getString("category_name"), null,
                    rs.getBoolean("category_active")));
        }
        return product;
    }
}
