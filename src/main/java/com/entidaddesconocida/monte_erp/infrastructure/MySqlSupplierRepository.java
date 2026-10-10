package com.entidaddesconocida.monte_erp.infrastructure;

import com.entidaddesconocida.monte_erp.domain.model.Supplier;
import com.entidaddesconocida.monte_erp.domain.repository.SupplierRepository;
import com.entidaddesconocida.monte_erp.infrastructure.config.DatabaseConnection;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC implementation of {@link SupplierRepository} for the {@code SUPPLIER}
 * table.
 *
 * <p>Read-only for RF-03: only the list needed to fill the product form and its
 * filter is implemented. The write operations arrive with RF-05.</p>
 */
public class MySqlSupplierRepository implements SupplierRepository {

    private static final String COLUMNS = "id, name, phone, email";
    private static final String SELECT_ALL = "SELECT " + COLUMNS + " FROM SUPPLIER";

    @Override
    public List<Supplier> findAll() throws SQLException {
        List<Supplier> suppliers = new ArrayList<>();
        try (Statement statement = DatabaseConnection.getConnection().createStatement();
             ResultSet rs = statement.executeQuery(SELECT_ALL + " ORDER BY name")) {
            while (rs.next()) {
                suppliers.add(map(rs));
            }
        }
        return suppliers;
    }

    private Supplier map(ResultSet rs) throws SQLException {
        return new Supplier(rs.getInt("id"), rs.getString("name"),
                rs.getString("phone"), rs.getString("email"));
    }
}
