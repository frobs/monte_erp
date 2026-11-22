package com.entidaddesconocida.monte_erp.infrastructure;

import com.entidaddesconocida.monte_erp.domain.model.Supplier;
import com.entidaddesconocida.monte_erp.domain.repository.SupplierRepository;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * JDBC implementation of {@link SupplierRepository} for the {@code SUPPLIER}
 * table.
 *
 * <p>The SQL and the JDBC plumbing are shared with customers in
 * {@link AbstractContactRepository}; only the table name and the row mapping
 * live here. The {@code active} flag supports the logical deletion introduced
 * by RF-05 (decision D1): {@code PURCHASE} references {@code SUPPLIER} with
 * {@code ON DELETE RESTRICT}, so rows are never removed.</p>
 */
public class MySqlSupplierRepository extends AbstractContactRepository<Supplier>
        implements SupplierRepository {

    private static final String TABLE = "SUPPLIER";

    public MySqlSupplierRepository(ConnectionFactory connections) {
        super(connections, TABLE);
    }

    @Override
    protected Supplier map(ResultSet rs) throws SQLException {
        return new Supplier(rs.getInt("id"), rs.getString("name"), rs.getString("phone"),
                rs.getString("email"), rs.getString("address"), rs.getBoolean("active"));
    }

    @Override
    protected void assignId(Supplier supplier, int id) {
        supplier.setId(id);
    }
}
