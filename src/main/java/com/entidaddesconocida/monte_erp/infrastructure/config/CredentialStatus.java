package com.entidaddesconocida.monte_erp.infrastructure.config;

/**
 * Outcome of reading a credential from the operating-system keyring.
 *
 * <p>It exists so that "there is no credential" ({@link #MISSING}) can be told
 * apart from "the keyring could not be read" ({@link #UNAVAILABLE}). Treating
 * both as the same {@code null} made a locked or unsupported backend
 * indistinguishable from a fresh installation (see S4).</p>
 */
public enum CredentialStatus {

    /** The credential was read: {@link CredentialLookup#value()} holds it. */
    OK,

    /** The backend answered and the account has no value stored. */
    MISSING,

    /**
     * The backend raised an error (locked keyring, missing secret service,
     * unsupported platform…). Whether the credential exists is unknown.
     */
    UNAVAILABLE
}
