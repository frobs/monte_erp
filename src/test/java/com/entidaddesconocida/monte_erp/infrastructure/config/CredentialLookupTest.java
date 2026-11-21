package com.entidaddesconocida.monte_erp.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link CredentialLookup}: the value and status combinations
 * that let callers tell a missing credential from an unreachable keyring (S4).
 */
class CredentialLookupTest {

    @Test
    @DisplayName("found guarda el valor y el estado OK")
    void foundKeepsTheValue() {
        CredentialLookup lookup = CredentialLookup.found("secret");

        assertEquals(CredentialStatus.OK, lookup.status());
        assertEquals("secret", lookup.value());
        assertTrue(lookup.isPresent());
        assertFalse(lookup.isMissing());
        assertFalse(lookup.isUnavailable());
    }

    @Test
    @DisplayName("Un valor nulo o en blanco se considera ausente, no disponible")
    void blankValuesAreMissing() {
        assertEquals(CredentialStatus.MISSING, CredentialLookup.of(null).status());
        assertEquals(CredentialStatus.MISSING, CredentialLookup.of("   ").status());
        assertNull(CredentialLookup.of("").value());
        assertTrue(CredentialLookup.of("").isMissing());
        assertFalse(CredentialLookup.of("").isUnavailable());
    }

    @Test
    @DisplayName("unavailable conserva el diagnóstico y no expone valor")
    void unavailableKeepsTheDiagnostic() {
        CredentialLookup lookup = CredentialLookup.unavailable("KeyringException: locked");

        assertEquals(CredentialStatus.UNAVAILABLE, lookup.status());
        assertNull(lookup.value());
        assertTrue(lookup.isUnavailable());
        assertFalse(lookup.isMissing());
        assertFalse(lookup.isPresent());
        assertEquals("KeyringException: locked", lookup.failure());
    }

    @Test
    @DisplayName("Un store que solo implementa get never reports unavailability")
    void defaultLookupFromPlainGetNeverReportsUnavailable() {
        CredentialStore legacy = new CredentialStore() {
            @Override
            public String get(String account) {
                return "value".equals(account) ? "secret" : null;
            }

            @Override
            public void set(String account, String value) {
                // No-op: this double only exercises reads.
            }

            @Override
            public void delete(String account) {
                // No-op: this double only exercises reads.
            }
        };

        assertEquals(CredentialStatus.OK, legacy.lookup("value").status());
        assertEquals(CredentialStatus.MISSING, legacy.lookup("other").status());
        assertFalse(legacy.lookup("other").isUnavailable());
    }
}
