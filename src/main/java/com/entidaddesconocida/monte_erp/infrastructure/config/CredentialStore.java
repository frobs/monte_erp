package com.entidaddesconocida.monte_erp.infrastructure.config;

/**
 * Minimal abstraction over the operating system keyring so the rest of the
 * infrastructure can be unit-tested without a native backend.
 *
 * <p>{@link KeyringCredentialStore} is the real implementation backed by the
 * {@code java-keyring} library.</p>
 */
public interface CredentialStore {

    /**
     * Returns the value stored under the given account, or {@code null} when it
     * does not exist or the backend is unavailable.
     */
    String get(String account);

    /**
     * Stores a value under the given account.
     *
     * @throws CredentialStoreException when the value cannot be written
     */
    void set(String account, String value) throws CredentialStoreException;

    /** Removes the value stored under the given account, if present. */
    void delete(String account) throws CredentialStoreException;
}
