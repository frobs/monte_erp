package com.entidaddesconocida.monte_erp.infrastructure;

import com.entidaddesconocida.monte_erp.domain.model.User;
import com.entidaddesconocida.monte_erp.infrastructure.config.DatabaseConnection;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Data access for the {@code USER} table.
 *
 * <p>No business logic: it only translates between {@link User} and the table
 * rows. Deleting a user is logical ({@link #deactivate(int)}), as required by
 * the E-R diagram.</p>
 */
public class UserRepository {

    private static final String COLUMNS = "id, name, username, password_hash, role, active";
    private static final String SELECT_ALL = "SELECT " + COLUMNS + " FROM USER";

    /**
     * Looks a user up by its login name (unique column {@code username}).
     *
     * @return the matching user, or {@code null} when it does not exist
     */
    public User findByUsername(String username) throws SQLException {
        String sql = SELECT_ALL + " WHERE username = ?";
        try (PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /** Returns every user, ordered by login name. */
    public List<User> findAll() throws SQLException {
        List<User> users = new ArrayList<>();
        try (Statement statement = DatabaseConnection.getConnection().createStatement();
             ResultSet rs = statement.executeQuery(SELECT_ALL + " ORDER BY username")) {
            while (rs.next()) {
                users.add(map(rs));
            }
        }
        return users;
    }

    /**
     * Inserts a new user.
     *
     * @return the generated identifier, which is also assigned to the object
     */
    public int insert(User user) throws SQLException {
        String sql = "INSERT INTO USER (name, username, password_hash, role, active) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = DatabaseConnection.getConnection()
                .prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.getName());
            ps.setString(2, user.getUsername());
            ps.setString(3, user.getPasswordHash());
            ps.setString(4, user.getRole());
            ps.setBoolean(5, user.isActive());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                int id = keys.next() ? keys.getInt(1) : 0;
                user.setId(id);
                return id;
            }
        }
    }

    /** Updates every editable field of an existing user. */
    public void update(User user) throws SQLException {
        String sql = "UPDATE USER SET name = ?, username = ?, password_hash = ?, role = ?, active = ? WHERE id = ?";
        try (PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement(sql)) {
            ps.setString(1, user.getName());
            ps.setString(2, user.getUsername());
            ps.setString(3, user.getPasswordHash());
            ps.setString(4, user.getRole());
            ps.setBoolean(5, user.isActive());
            ps.setInt(6, user.getId());
            ps.executeUpdate();
        }
    }

    /**
     * Logical deletion: sets {@code active} to 0 without removing the row, so
     * the foreign keys of the purchases and orders already recorded stay valid.
     */
    public void deactivate(int id) throws SQLException {
        try (PreparedStatement ps = DatabaseConnection.getConnection()
                .prepareStatement("UPDATE USER SET active = 0 WHERE id = ?")) {
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
        user.setRole(rs.getString("role"));
        user.setActive(rs.getBoolean("active"));
        return user;
    }
}
