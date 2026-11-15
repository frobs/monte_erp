package com.entidaddesconocida.monte_erp.infrastructure;

import com.entidaddesconocida.monte_erp.domain.model.Role;
import com.entidaddesconocida.monte_erp.domain.model.User;
import com.entidaddesconocida.monte_erp.domain.repository.UserRepository;
import com.entidaddesconocida.monte_erp.infrastructure.config.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC implementation of {@link UserRepository} for the {@code USER} table.
 *
 * <p>No business logic: it only translates between {@link User} and the table
 * rows. Deleting a user is logical ({@link #deactivate(int)}), as required by
 * the E-R diagram.</p>
 */
public class MySqlUserRepository implements UserRepository {

    private static final String COLUMNS = "id, name, username, password_hash, role, active";
    private static final String SELECT_ALL = "SELECT " + COLUMNS + " FROM USER";
    private static final String INSERT_USER =
            "INSERT INTO USER (name, username, password_hash, role, active) VALUES (?, ?, ?, ?, ?)";
    private static final String UPDATE_USER =
            "UPDATE USER SET name = ?, username = ?, password_hash = ?, role = ?, active = ? WHERE id = ?";
    private static final String DEACTIVATE_USER = "UPDATE USER SET active = 0 WHERE id = ?";

    @Override
    public User findByUsername(String username) throws SQLException {
        String sql = SELECT_ALL + " WHERE username = ?";
        try (Connection connection = DatabaseConnection.open();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    @Override
    public List<User> findAll() throws SQLException {
        List<User> users = new ArrayList<>();
        try (Connection connection = DatabaseConnection.open();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(SELECT_ALL + " ORDER BY username")) {
            while (rs.next()) {
                users.add(map(rs));
            }
        }
        return users;
    }

    @Override
    public int insert(User user) throws SQLException {
        try (Connection connection = DatabaseConnection.open();
             PreparedStatement ps = connection
                     .prepareStatement(INSERT_USER, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.getName());
            ps.setString(2, user.getUsername());
            ps.setString(3, user.getPasswordHash());
            ps.setString(4, user.getRole().dbValue());
            ps.setBoolean(5, user.isActive());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                int id = keys.next() ? keys.getInt(1) : 0;
                user.setId(id);
                return id;
            }
        }
    }

    @Override
    public void update(User user) throws SQLException {
        try (Connection connection = DatabaseConnection.open();
             PreparedStatement ps = connection.prepareStatement(UPDATE_USER)) {
            ps.setString(1, user.getName());
            ps.setString(2, user.getUsername());
            ps.setString(3, user.getPasswordHash());
            ps.setString(4, user.getRole().dbValue());
            ps.setBoolean(5, user.isActive());
            ps.setInt(6, user.getId());
            ps.executeUpdate();
        }
    }

    @Override
    public void deactivate(int id) throws SQLException {
        try (Connection connection = DatabaseConnection.open();
             PreparedStatement ps = connection.prepareStatement(DEACTIVATE_USER)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    private User map(ResultSet rs) throws SQLException {
        User user = new User();
        user.setId(rs.getInt("id"));
        user.setName(rs.getString("name"));
        user.setUsername(rs.getString("username"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setRole(Role.fromDb(rs.getString("role")));
        user.setActive(rs.getBoolean("active"));
        return user;
    }
}
