package com.entidaddesconocida.monte_erp.infrastructure;

import com.entidaddesconocida.monte_erp.domain.model.Customer;
import com.entidaddesconocida.monte_erp.domain.repository.CustomerRepository;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * JDBC implementation of {@link CustomerRepository} for the {@code CUSTOMER}
 * table.
 *
 * <p>The SQL and the JDBC plumbing are shared with suppliers in
 * {@link AbstractContactRepository}; only the table name and the row mapping
 * live here. The {@code active} flag supports the logical deletion introduced
 * by RF-06 (decision D1): {@code SALES_ORDER} references {@code CUSTOMER} with
 * {@code ON DELETE RESTRICT}, so rows are never removed.</p>
 */
public class MySqlCustomerRepository extends AbstractContactRepository<Customer>
        implements CustomerRepository {

    private static final String TABLE = "CUSTOMER";

    public MySqlCustomerRepository(ConnectionFactory connections) {
        super(connections, TABLE);
    }

    @Override
    protected Customer map(ResultSet rs) throws SQLException {
        return new Customer(rs.getInt("id"), rs.getString("name"), rs.getString("phone"),
                rs.getString("email"), rs.getString("address"), rs.getBoolean("active"));
    }

    @Override
    protected void assignId(Customer customer, int id) {
        customer.setId(id);
    }
}
