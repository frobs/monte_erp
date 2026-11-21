package com.entidaddesconocida.monte_erp.infrastructure.config;

/**
 * Minimal abstraction over the operating system keyring so the rest of the
 * infrastructure can be unit-tested without a native backend.
 *
 * <p>{@link KeyringCredentialStore} is the real implementation backed by the
 * {@code java-keyring} library.</p>
 *
 * <p>Reads report a {@link CredentialLookup} instead of a bare {@code null} so
 * the caller can tell "there is no credential" from "the keyring could not be
 * read". Implementations that cannot make that distinction can keep overriding
 * {@link #get(String)}: the default {@link #lookup(String)} reports
 * {@link CredentialStatus#OK} or {@link CredentialStatus#MISSING}, never
 * {@link CredentialStatus#UNAVAILABLE}.</p>
 */
public interface CredentialStore {

    /**
     * Reads the value stored under the given account, reporting whether it is
     * missing or the backend was unavailable.
     */
    default CredentialLookup lookup(String account) {
        return CredentialLookup.of(get(account));
    }

    /**
     * Returns the value stored under the given account, or {@code null} when it
     * does not exist or the backend is unavailable.
     *
     * <p>Prefer {@link #lookup(String)} when the reason matters: this method
     * collapses both cases into {@code null}.</p>
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
