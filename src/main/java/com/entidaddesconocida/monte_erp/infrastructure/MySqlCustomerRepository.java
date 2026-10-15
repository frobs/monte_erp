package com.entidaddesconocida.monte_erp.infrastructure;

import com.entidaddesconocida.monte_erp.domain.model.Customer;
import com.entidaddesconocida.monte_erp.domain.repository.CustomerRepository;
import com.entidaddesconocida.monte_erp.infrastructure.config.DatabaseConnection;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC implementation of {@link CustomerRepository} for the {@code CUSTOMER}
 * table.
 *
 * <p>No business logic: it only translates between {@link Customer} and the table
 * rows. The {@code active} flag supports the logical deletion introduced by RF-06
 * (decision D1), so {@link #deactivate(int)} / {@link #activate(int)} never
 * remove the row: {@code SALES_ORDER} references {@code CUSTOMER} with
 * {@code ON DELETE RESTRICT}.</p>
 */
public class MySqlCustomerRepository implements CustomerRepository {

    private static final String COLUMNS = "id, name, phone, email, address, active";
    private static final String SELECT_ALL = "SELECT " + COLUMNS + " FROM CUSTOMER";
    private static final String INSERT_CUSTOMER =
            "INSERT INTO CUSTOMER (name, phone, email, address, active) VALUES (?, ?, ?, ?, 1)";
    private static final String UPDATE_CUSTOMER =
            "UPDATE CUSTOMER SET name = ?, phone = ?, email = ?, address = ? WHERE id = ?";
    private static final String DEACTIVATE_CUSTOMER = "UPDATE CUSTOMER SET active = 0 WHERE id = ?";
    private static final String ACTIVATE_CUSTOMER = "UPDATE CUSTOMER SET active = 1 WHERE id = ?";

    @Override
    public List<Customer> findAll() throws SQLException {
        return query(SELECT_ALL + " ORDER BY name");
    }

    @Override
    public List<Customer> findActive() throws SQLException {
        return query(SELECT_ALL + " WHERE active = 1 ORDER BY name");
    }

    @Override
    public List<Customer> findInactive() throws SQLException {
        return query(SELECT_ALL + " WHERE active = 0 ORDER BY name");
    }

    @Override
    public Customer findByName(String name) throws SQLException {
        String sql = SELECT_ALL + " WHERE name = ?";
        try (PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    @Override
    public int insert(Customer customer) throws SQLException {
        try (PreparedStatement ps = DatabaseConnection.getConnection()
                .prepareStatement(INSERT_CUSTOMER, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, customer.getName());
            ps.setString(2, customer.getPhone());
            ps.setString(3, customer.getEmail());
            ps.setString(4, customer.getAddress());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                int id = keys.next() ? keys.getInt(1) : 0;
                customer.setId(id);
                return id;
            }
        }
    }

    @Override
    public void update(Customer customer) throws SQLException {
        // The active flag is deliberately absent: only deactivate/activate change it.
        try (PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement(UPDATE_CUSTOMER)) {
            ps.setString(1, customer.getName());
            ps.setString(2, customer.getPhone());
            ps.setString(3, customer.getEmail());
            ps.setString(4, customer.getAddress());
            ps.setInt(5, customer.getId());
            ps.executeUpdate();
        }
    }

    @Override
    public void deactivate(int id) throws SQLException {
        setActive(DEACTIVATE_CUSTOMER, id);
    }

    @Override
    public void activate(int id) throws SQLException {
        setActive(ACTIVATE_CUSTOMER, id);
    }

    private void setActive(String sql, int id) throws SQLException {
        try (PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    private List<Customer> query(String sql) throws SQLException {
        List<Customer> customers = new ArrayList<>();
        try (Statement statement = DatabaseConnection.getConnection().createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                customers.add(map(rs));
            }
        }
        return customers;
    }

    private Customer map(ResultSet rs) throws SQLException {
        return new Customer(rs.getInt("id"), rs.getString("name"), rs.getString("phone"),
                rs.getString("email"), rs.getString("address"), rs.getBoolean("active"));
    }
}
