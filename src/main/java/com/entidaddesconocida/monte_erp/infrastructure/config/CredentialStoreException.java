package com.entidaddesconocida.monte_erp.infrastructure.config;

/**
 * Unchecked failure while reading or writing a credential in the operating
 * system keyring.
 */
public final class CredentialStoreException extends RuntimeException {

    public CredentialStoreException(String message) {
        super(message);
    }

    public CredentialStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
