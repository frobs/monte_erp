package com.entidaddesconocida.monte_erp.infrastructure;

import com.entidaddesconocida.monte_erp.domain.model.Category;
import com.entidaddesconocida.monte_erp.domain.model.Product;
import com.entidaddesconocida.monte_erp.domain.repository.ProductRepository;
import com.entidaddesconocida.monte_erp.infrastructure.config.DatabaseConnection;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

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

    private static final String COLUMNS = """
            p.id, p.name, p.sku, p.description, p.price, p.min_stock, p.active,
            p.category_id, c.name AS category_name
            """;
    // LEFT JOIN so a product without a category (RF-03 makes it optional) is
    // still listed, with a NULL category_name.
    private static final String SELECT_ALL = "SELECT " + COLUMNS + " FROM PRODUCT p"
            + " LEFT JOIN CATEGORY c ON p.category_id = c.id";
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
    public List<Product> findInactive() throws SQLException {
        return query(SELECT_ALL + " WHERE p.active = 0 ORDER BY p.name");
    }

    @Override
    public Product findBySku(String sku) throws SQLException {
        String sql = SELECT_ALL + " WHERE p.sku = ?";
        try (PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement(sql)) {
            ps.setString(1, sku);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    @Override
    public int insert(Product product) throws SQLException {
        try (PreparedStatement ps = DatabaseConnection.getConnection()
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
        try (PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement(UPDATE_PRODUCT)) {
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
        try (PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement(sql)) {
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
        try (Statement statement = DatabaseConnection.getConnection().createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                products.add(map(rs));
            }
        }
        return products;
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
        // Only id and name are needed: the combo/list render the name and the
        // foreign key is written from the id. A missing category stays null
        // instead of becoming Category(0, null).
        int categoryId = rs.getInt("category_id");
        if (!rs.wasNull()) {
            product.setCategory(new Category(categoryId, rs.getString("category_name"), null));
        }
        return product;
    }
}
