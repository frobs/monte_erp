package com.entidaddesconocida.monte_erp.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Password hashing tests: it is the critical piece of RF-01, so the suite checks
 * that no code path returns {@code true} by accident.
 */
class PasswordHasherTest {

    @Test
    @DisplayName("hash() and verify() agree for the correct password")
    void hashAndVerifyAgree() {
        String hash = PasswordHasher.hash("admin123");
        assertTrue(PasswordHasher.verify("admin123", hash));
    }

    @Test
    @DisplayName("A wrong password is rejected")
    void wrongPasswordIsRejected() {
        String hash = PasswordHasher.hash("admin123");
        assertFalse(PasswordHasher.verify("admin124", hash));
        assertFalse(PasswordHasher.verify("Admin123", hash));
        assertFalse(PasswordHasher.verify("", hash));
    }

    @Test
    @DisplayName("The hash uses PBKDF2-HMAC-SHA256 with a random salt and hides the password")
    void hashDoesNotRevealThePassword() {
        String hash = PasswordHasher.hash("admin123");
        assertTrue(hash.startsWith("pbkdf2-sha256$210000$"), "expected format: " + hash);
        assertFalse(hash.contains("admin123"), "the hash must not contain the password in clear text");
        assertNotEquals(hash, PasswordHasher.hash("admin123"), "two equal hashes => the salt is not random");
    }

    @Test
    @DisplayName("A corrupted or unknown hash format is rejected without throwing")
    void corruptedHashIsRejected() {
        assertFalse(PasswordHasher.verify("admin123", null));
        assertFalse(PasswordHasher.verify("admin123", ""));
        assertFalse(PasswordHasher.verify("admin123", "plain-text-admin123"));
        assertFalse(PasswordHasher.verify("admin123", "pbkdf2-sha256$210000$not-base64!$neither"));
        assertFalse(PasswordHasher.verify("admin123", "other-format$210000$c2FsdA==$a2V5"));
        assertFalse(PasswordHasher.verify("admin123", "pbkdf2-sha256$0$c2FsdA==$a2V5"));
        assertTrue(PasswordHasher.verify("admin123", PasswordHasher.hash("admin123")));
    }

    @Test
    @DisplayName("Hashing an empty password is not allowed")
    void emptyPasswordIsRejectedWhenHashing() {
        assertThrows(IllegalArgumentException.class, () -> PasswordHasher.hash(null));
        assertThrows(IllegalArgumentException.class, () -> PasswordHasher.hash(""));
    }
}
