package com.entidaddesconocida.monte_erp.domain.service;

/**
 * Failure while loading, testing or persisting the database configuration, or
 * while provisioning the database (migrations + administrator user).
 *
 * <p>The message keeps the underlying technical cause; the ViewModel decides how
 * to present it to the user.</p>
 */
public final class SetupException extends Exception {

    public SetupException(String message) {
        super(message);
    }

    public SetupException(String message, Throwable cause) {
        super(message, cause);
    }
}
