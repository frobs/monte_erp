package com.entidaddesconocida.monte_erp.infrastructure;

import com.entidaddesconocida.monte_erp.domain.model.Category;
import com.entidaddesconocida.monte_erp.domain.repository.CategoryRepository;
import com.entidaddesconocida.monte_erp.infrastructure.config.DatabaseConnection;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC implementation of {@link CategoryRepository} for the {@code CATEGORY}
 * table.
 *
 * <p>Read-only for RF-03: only the list needed to fill the product form and its
 * filter is implemented. The write operations arrive with RF-04.</p>
 */
public class MySqlCategoryRepository implements CategoryRepository {

    private static final String COLUMNS = "id, name, description";
    private static final String SELECT_ALL = "SELECT " + COLUMNS + " FROM CATEGORY";

    @Override
    public List<Category> findAll() throws SQLException {
        List<Category> categories = new ArrayList<>();
        try (Statement statement = DatabaseConnection.getConnection().createStatement();
             ResultSet rs = statement.executeQuery(SELECT_ALL + " ORDER BY name")) {
            while (rs.next()) {
                categories.add(map(rs));
            }
        }
        return categories;
    }

    private Category map(ResultSet rs) throws SQLException {
        return new Category(rs.getInt("id"), rs.getString("name"), rs.getString("description"));
    }
}
