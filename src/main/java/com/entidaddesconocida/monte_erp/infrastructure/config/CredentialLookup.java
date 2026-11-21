package com.entidaddesconocida.monte_erp.infrastructure.config;

/**
 * Result of a {@link CredentialStore} read: the status plus the value, when it
 * exists.
 *
 * @param status  what the backend answered, never {@code null}
 * @param value   the credential when {@code status == OK}, {@code null} otherwise
 * @param failure diagnostic message of the backend when
 *                {@code status == UNAVAILABLE}; never shown to the user
 */
public record CredentialLookup(CredentialStatus status, String value, String failure) {

    private static final CredentialLookup MISSING =
            new CredentialLookup(CredentialStatus.MISSING, null, null);

    public CredentialLookup {
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
    }

    /** The credential exists. */
    public static CredentialLookup found(String value) {
        if (value == null || value.isBlank()) {
            return MISSING;
        }
        return new CredentialLookup(CredentialStatus.OK, value, null);
    }

    /** Leftovers of a store that cannot tell the two failure modes apart. */
    public static CredentialLookup of(String value) {
        return found(value);
    }

    /** The backend answered and there is nothing stored for the account. */
    public static CredentialLookup missing() {
        return MISSING;
    }

    /** The backend could not be read; {@code failure} is for diagnostics only. */
    public static CredentialLookup unavailable(String failure) {
        return new CredentialLookup(CredentialStatus.UNAVAILABLE, null, failure);
    }

    /** {@code true} when the keyring could not be read, so the value is unknown. */
    public boolean isUnavailable() {
        return status == CredentialStatus.UNAVAILABLE;
    }

    /** {@code true} when the account has no stored value. */
    public boolean isMissing() {
        return status == CredentialStatus.MISSING;
    }

    /** {@code true} when the value was read. */
    public boolean isPresent() {
        return status == CredentialStatus.OK;
    }
}
