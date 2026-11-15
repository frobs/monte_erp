package com.entidaddesconocida.monte_erp.infrastructure;

import com.entidaddesconocida.monte_erp.domain.model.Supplier;
import com.entidaddesconocida.monte_erp.domain.repository.SupplierRepository;
import com.entidaddesconocida.monte_erp.infrastructure.config.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC implementation of {@link SupplierRepository} for the {@code SUPPLIER}
 * table.
 *
 * <p>No business logic: it only translates between {@link Supplier} and the table
 * rows. The {@code active} flag supports the logical deletion introduced by RF-05
 * (decision D1), so {@link #deactivate(int)} / {@link #activate(int)} never
 * remove the row: {@code PURCHASE} references {@code SUPPLIER} with
 * {@code ON DELETE RESTRICT}.</p>
 */
public class MySqlSupplierRepository implements SupplierRepository {

    private static final String COLUMNS = "id, name, phone, email, address, active";
    private static final String SELECT_ALL = "SELECT " + COLUMNS + " FROM SUPPLIER";
    private static final String SELECT_ACTIVE_SEARCH = SELECT_ALL
            + " WHERE active = 1"
            + " AND (name LIKE ? ESCAPE '!' OR phone LIKE ? ESCAPE '!' OR email LIKE ? ESCAPE '!')"
            + " ORDER BY name";
    private static final String SELECT_INACTIVE_SEARCH = SELECT_ALL
            + " WHERE active = 0"
            + " AND (name LIKE ? ESCAPE '!' OR phone LIKE ? ESCAPE '!' OR email LIKE ? ESCAPE '!')"
            + " ORDER BY name";
    private static final String INSERT_SUPPLIER =
            "INSERT INTO SUPPLIER (name, phone, email, address, active) VALUES (?, ?, ?, ?, 1)";
    private static final String UPDATE_SUPPLIER =
            "UPDATE SUPPLIER SET name = ?, phone = ?, email = ?, address = ? WHERE id = ?";
    private static final String DEACTIVATE_SUPPLIER = "UPDATE SUPPLIER SET active = 0 WHERE id = ?";
    private static final String ACTIVATE_SUPPLIER = "UPDATE SUPPLIER SET active = 1 WHERE id = ?";

    @Override
    public List<Supplier> findAll() throws SQLException {
        return query(SELECT_ALL + " ORDER BY name");
    }

    @Override
    public List<Supplier> findActive() throws SQLException {
        return query(SELECT_ALL + " WHERE active = 1 ORDER BY name");
    }

    @Override
    public List<Supplier> findActive(String search) throws SQLException {
        String pattern = SqlSearch.likePattern(search);
        return pattern == null
                ? findActive()
                : query(SELECT_ACTIVE_SEARCH, pattern, pattern, pattern);
    }

    @Override
    public List<Supplier> findInactive() throws SQLException {
        return query(SELECT_ALL + " WHERE active = 0 ORDER BY name");
    }

    @Override
    public List<Supplier> findInactive(String search) throws SQLException {
        String pattern = SqlSearch.likePattern(search);
        return pattern == null
                ? findInactive()
                : query(SELECT_INACTIVE_SEARCH, pattern, pattern, pattern);
    }

    @Override
    public Supplier findByName(String name) throws SQLException {
        String sql = SELECT_ALL + " WHERE name = ?";
        try (Connection connection = DatabaseConnection.open();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    @Override
    public int insert(Supplier supplier) throws SQLException {
        try (Connection connection = DatabaseConnection.open();
             PreparedStatement ps = connection
                     .prepareStatement(INSERT_SUPPLIER, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, supplier.getName());
            ps.setString(2, supplier.getPhone());
            ps.setString(3, supplier.getEmail());
            ps.setString(4, supplier.getAddress());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                int id = keys.next() ? keys.getInt(1) : 0;
                supplier.setId(id);
                return id;
            }
        }
    }

    @Override
    public void update(Supplier supplier) throws SQLException {
        // The active flag is deliberately absent: only deactivate/activate change it.
        try (Connection connection = DatabaseConnection.open();
             PreparedStatement ps = connection.prepareStatement(UPDATE_SUPPLIER)) {
            ps.setString(1, supplier.getName());
            ps.setString(2, supplier.getPhone());
            ps.setString(3, supplier.getEmail());
            ps.setString(4, supplier.getAddress());
            ps.setInt(5, supplier.getId());
            ps.executeUpdate();
        }
    }

    @Override
    public void deactivate(int id) throws SQLException {
        setActive(DEACTIVATE_SUPPLIER, id);
    }

    @Override
    public void activate(int id) throws SQLException {
        setActive(ACTIVATE_SUPPLIER, id);
    }

    private void setActive(String sql, int id) throws SQLException {
        try (Connection connection = DatabaseConnection.open();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    private List<Supplier> query(String sql, String... parameters) throws SQLException {
        List<Supplier> suppliers = new ArrayList<>();
        if (parameters.length == 0) {
            try (Connection connection = DatabaseConnection.open();
                 Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery(sql)) {
                while (rs.next()) {
                    suppliers.add(map(rs));
                }
            }
            return suppliers;
        }
        try (Connection connection = DatabaseConnection.open();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                ps.setString(index + 1, parameters[index]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    suppliers.add(map(rs));
                }
            }
        }
        return suppliers;
    }

    private Supplier map(ResultSet rs) throws SQLException {
        return new Supplier(rs.getInt("id"), rs.getString("name"), rs.getString("phone"),
                rs.getString("email"), rs.getString("address"), rs.getBoolean("active"));
    }
}
