package com.entidaddesconocida.monte_erp.infrastructure;

import com.entidaddesconocida.monte_erp.domain.model.Contact;
import com.entidaddesconocida.monte_erp.domain.repository.ContactRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * JDBC template shared by the contact repositories ({@code CUSTOMER} and
 * {@code SUPPLIER}).
 *
 * <p>Both tables have the same columns and the same logical-deletion queries, so
 * the SQL and the JDBC plumbing live here once and the table name is the only
 * difference. A concrete repository only maps a row to its entity and assigns
 * the generated id.</p>
 *
 * <p>No business logic: it only translates between rows and entities. The
 * {@code active} flag supports the logical deletion introduced by RF-05/RF-06
 * (decision D1), so {@link #deactivate(int)} / {@link #activate(int)} never
 * remove the row: {@code PURCHASE} and {@code SALES_ORDER} reference these
 * tables with {@code ON DELETE RESTRICT}.</p>
 *
 * @param <T> contact entity of the table
 */
abstract class AbstractContactRepository<T extends Contact> implements ContactRepository<T> {

    private static final String COLUMNS = "id, name, phone, email, address, active";

    private final ConnectionFactory connections;
    private final String selectAll;
    private final String selectActiveSearch;
    private final String selectInactiveSearch;
    private final String insertSql;
    private final String updateSql;
    private final String deactivateSql;
    private final String activateSql;

    protected AbstractContactRepository(ConnectionFactory connections, String table) {
        this.connections = Objects.requireNonNull(connections, "connections");
        Objects.requireNonNull(table, "table");
        this.selectAll = "SELECT " + COLUMNS + " FROM " + table;
        this.selectActiveSearch = selectAll
                + " WHERE active = 1"
                + " AND (name LIKE ? ESCAPE '!' OR phone LIKE ? ESCAPE '!' OR email LIKE ? ESCAPE '!')"
                + " ORDER BY name";
        this.selectInactiveSearch = selectAll
                + " WHERE active = 0"
                + " AND (name LIKE ? ESCAPE '!' OR phone LIKE ? ESCAPE '!' OR email LIKE ? ESCAPE '!')"
                + " ORDER BY name";
        this.insertSql = "INSERT INTO " + table + " (name, phone, email, address, active) VALUES (?, ?, ?, ?, 1)";
        this.updateSql = "UPDATE " + table + " SET name = ?, phone = ?, email = ?, address = ? WHERE id = ?";
        this.deactivateSql = "UPDATE " + table + " SET active = 0 WHERE id = ?";
        this.activateSql = "UPDATE " + table + " SET active = 1 WHERE id = ?";
    }

    /** Maps the current row of {@code rs} to the concrete entity. */
    protected abstract T map(ResultSet rs) throws SQLException;

    /** Writes the generated id back into the entity inserted by {@link #insert}. */
    protected abstract void assignId(T entity, int id);

    public List<T> findAll() throws SQLException {
        return query(selectAll + " ORDER BY name");
    }

    public List<T> findActive() throws SQLException {
        return query(selectAll + " WHERE active = 1 ORDER BY name");
    }

    @Override
    public List<T> findActive(String search) throws SQLException {
        String pattern = SqlSearch.likePattern(search);
        return pattern == null
                ? findActive()
                : query(selectActiveSearch, pattern, pattern, pattern);
    }

    public List<T> findInactive() throws SQLException {
        return query(selectAll + " WHERE active = 0 ORDER BY name");
    }

    @Override
    public List<T> findInactive(String search) throws SQLException {
        String pattern = SqlSearch.likePattern(search);
        return pattern == null
                ? findInactive()
                : query(selectInactiveSearch, pattern, pattern, pattern);
    }

    @Override
    public T findByName(String name) throws SQLException {
        String sql = selectAll + " WHERE name = ?";
        try (Connection connection = connections.open();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    @Override
    public int insert(T entity) throws SQLException {
        try (Connection connection = connections.open();
             PreparedStatement ps = connection.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, entity.getName());
            ps.setString(2, entity.getPhone());
            ps.setString(3, entity.getEmail());
            ps.setString(4, entity.getAddress());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                int id = keys.next() ? keys.getInt(1) : 0;
                assignId(entity, id);
                return id;
            }
        }
    }

    @Override
    public void update(T entity) throws SQLException {
        // The active flag is deliberately absent: only deactivate/activate change it.
        try (Connection connection = connections.open();
             PreparedStatement ps = connection.prepareStatement(updateSql)) {
            ps.setString(1, entity.getName());
            ps.setString(2, entity.getPhone());
            ps.setString(3, entity.getEmail());
            ps.setString(4, entity.getAddress());
            ps.setInt(5, entity.getId());
            ps.executeUpdate();
        }
    }

    @Override
    public void deactivate(int id) throws SQLException {
        setActive(deactivateSql, id);
    }

    @Override
    public void activate(int id) throws SQLException {
        setActive(activateSql, id);
    }

    private void setActive(String sql, int id) throws SQLException {
        try (Connection connection = connections.open();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    private List<T> query(String sql, String... parameters) throws SQLException {
        List<T> results = new ArrayList<>();
        if (parameters.length == 0) {
            try (Connection connection = connections.open();
                 Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery(sql)) {
                while (rs.next()) {
                    results.add(map(rs));
                }
            }
            return results;
        }
        try (Connection connection = connections.open();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                ps.setString(index + 1, parameters[index]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(map(rs));
                }
            }
        }
        return results;
    }
}
