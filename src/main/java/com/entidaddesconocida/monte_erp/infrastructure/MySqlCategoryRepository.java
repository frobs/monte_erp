package com.entidaddesconocida.monte_erp.infrastructure;

import com.entidaddesconocida.monte_erp.domain.model.Category;
import com.entidaddesconocida.monte_erp.domain.repository.CategoryRepository;
import com.entidaddesconocida.monte_erp.infrastructure.config.DatabaseConnection;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC implementation of {@link CategoryRepository} for the {@code CATEGORY}
 * table.
 *
 * <p>No business logic: it only translates between {@link Category} and the table
 * rows. The {@code active} flag supports the logical deletion introduced by RF-04
 * (decision D2), so {@link #deactivate(int)} / {@link #activate(int)} never
 * remove the row: {@code PRODUCT} references {@code CATEGORY} with
 * {@code ON DELETE RESTRICT}.</p>
 */
public class MySqlCategoryRepository implements CategoryRepository {

    private static final String COLUMNS = "id, name, description, active";
    private static final String SELECT_ALL = "SELECT " + COLUMNS + " FROM CATEGORY";
    private static final String INSERT_CATEGORY =
            "INSERT INTO CATEGORY (name, description, active) VALUES (?, ?, 1)";
    private static final String UPDATE_CATEGORY =
            "UPDATE CATEGORY SET name = ?, description = ? WHERE id = ?";
    private static final String DEACTIVATE_CATEGORY = "UPDATE CATEGORY SET active = 0 WHERE id = ?";
    private static final String ACTIVATE_CATEGORY = "UPDATE CATEGORY SET active = 1 WHERE id = ?";

    @Override
    public List<Category> findAll() throws SQLException {
        return query(SELECT_ALL + " ORDER BY name");
    }

    @Override
    public List<Category> findActive() throws SQLException {
        return query(SELECT_ALL + " WHERE active = 1 ORDER BY name");
    }

    @Override
    public List<Category> findInactive() throws SQLException {
        return query(SELECT_ALL + " WHERE active = 0 ORDER BY name");
    }

    @Override
    public Category findByName(String name) throws SQLException {
        String sql = SELECT_ALL + " WHERE name = ?";
        try (PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    @Override
    public int insert(Category category) throws SQLException {
        try (PreparedStatement ps = DatabaseConnection.getConnection()
                .prepareStatement(INSERT_CATEGORY, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, category.getName());
            ps.setString(2, category.getDescription());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                int id = keys.next() ? keys.getInt(1) : 0;
                category.setId(id);
                return id;
            }
        }
    }

    @Override
    public void update(Category category) throws SQLException {
        // The active flag is deliberately absent: only deactivate/activate change it.
        try (PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement(UPDATE_CATEGORY)) {
            ps.setString(1, category.getName());
            ps.setString(2, category.getDescription());
            ps.setInt(3, category.getId());
            ps.executeUpdate();
        }
    }

    @Override
    public void deactivate(int id) throws SQLException {
        setActive(DEACTIVATE_CATEGORY, id);
    }

    @Override
    public void activate(int id) throws SQLException {
        setActive(ACTIVATE_CATEGORY, id);
    }

    private void setActive(String sql, int id) throws SQLException {
        try (PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    private List<Category> query(String sql) throws SQLException {
        List<Category> categories = new ArrayList<>();
        try (Statement statement = DatabaseConnection.getConnection().createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                categories.add(map(rs));
            }
        }
        return categories;
    }

    private Category map(ResultSet rs) throws SQLException {
        return new Category(rs.getInt("id"), rs.getString("name"), rs.getString("description"),
                rs.getBoolean("active"));
    }
}
