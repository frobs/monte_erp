package com.entidaddesconocida.monte_erp.infrastructure;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Source of JDBC connections for the infrastructure adapters.
 *
 * <p>Every repository receives one through its constructor instead of reading a
 * global configuration. That keeps the connection settings scoped to the
 * composition root and lets tests create independent factories without touching
 * shared state (A7).</p>
 *
 * <p>The contract is still <strong>one connection per operation</strong>: there
 * is no shared {@link Connection}. A single mutable connection reused by every
 * repository forced the transactional repositories to flip {@code autoCommit} on
 * global state and restore it on {@code finally}, and it only worked while all
 * the database work happened on the JavaFX thread; two concurrent operations (a
 * background query, a virtual thread) would have committed each other's work.
 * Opening a connection per operation costs nothing at this scale (a local MySQL)
 * and removes both problems: every {@link #withTransaction(TransactedWork)} block
 * is isolated, and a stale connection can never be handed out, so a dropped
 * connection (MySQL restarted, container recreated) simply becomes a failed
 * operation the caller reports, not a poisoned global.</p>
 */
public interface ConnectionFactory {

    /**
     * Opens a new connection. The caller owns it and must close it (prefer
     * {@link #withConnection(SqlWork)} or {@link #withTransaction(TransactedWork)}).
     *
     * @throws SQLException when the connection cannot be established or the
     *                      configuration is missing
     */
    Connection open() throws SQLException;

    /**
     * JDBC URL of the current configuration (handy to display the environment in
     * the interface).
     */
    String url();

    /**
     * Runs {@code work} with its own connection, closing it afterwards. Statement
     * errors propagate; the connection is always closed, also on failure.
     */
    default <T> T withConnection(SqlWork<T> work) throws SQLException {
        try (Connection connection = open()) {
            return work.run(connection);
        }
    }

    /**
     * Runs {@code work} inside a transaction on its own connection: commits when
     * it returns and rolls the whole block back when it throws, so the caller sees
     * the original exception (with the rollback failure attached as suppressed when
     * there is one).
     */
    default <T, E extends Exception> T withTransaction(TransactedWork<T, E> work)
            throws SQLException, E {
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (Exception failure) {
                // Precise rethrow: the compiler infers SQLException | E | RuntimeException.
                rollback(connection, failure);
                throw failure;
            }
        }
    }

    /**
     * Rolls the transaction back, attaching the rollback failure to the original
     * exception so the real cause is never lost.
     */
    private static void rollback(Connection connection, Exception failure) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }

    /** Unit of work that receives the connection of the current operation. */
    @FunctionalInterface
    interface SqlWork<T> {
        T run(Connection connection) throws SQLException;
    }

    /**
     * Transactional unit of work: it may throw the domain exceptions of the
     * adapter (checked), which propagate unchanged after the rollback.
     */
    @FunctionalInterface
    interface TransactedWork<T, E extends Exception> {
        T run(Connection connection) throws SQLException, E;
    }
}
