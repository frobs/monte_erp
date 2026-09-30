package com.entidaddesconocida.monte_erp.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del hash de contraseñas: es la pieza crítica de RF-01, así que se
 * verifica que ningún camino devuelve {@code true} por accidente.
 */
class PasswordHasherTest {

    @Test
    @DisplayName("hash() y verify() son coherentes para la contraseña correcta")
    void hashYVerifyCoherentes() {
        String hash = PasswordHasher.hash("admin123");
        assertTrue(PasswordHasher.verify("admin123", hash));
    }

    @Test
    @DisplayName("La contraseña incorrecta se rechaza")
    void contrasenaIncorrecta() {
        String hash = PasswordHasher.hash("admin123");
        assertFalse(PasswordHasher.verify("admin124", hash));
        assertFalse(PasswordHasher.verify("Admin123", hash));
        assertFalse(PasswordHasher.verify("", hash));
    }

    @Test
    @DisplayName("El hash usa PBKDF2-HMAC-SHA256 con salt aleatorio y no contiene la contraseña")
    void hashNoRevelaLaContrasena() {
        String hash = PasswordHasher.hash("admin123");
        assertTrue(hash.startsWith("pbkdf2-sha256$210000$"), "formato esperado: " + hash);
        assertFalse(hash.contains("admin123"), "el hash no debe contener la contraseña en claro");
        assertNotEquals(hash, PasswordHasher.hash("admin123"), "dos hashes iguales => salt no aleatorio");
    }

    @Test
    @DisplayName("Un hash corrupto o de formato desconocido se rechaza sin lanzar excepción")
    void hashCorrupto() {
        assertFalse(PasswordHasher.verify("admin123", null));
        assertFalse(PasswordHasher.verify("admin123", ""));
        assertFalse(PasswordHasher.verify("admin123", "texto-en-claro-admin123"));
        assertFalse(PasswordHasher.verify("admin123", "pbkdf2-sha256$210000$no-base64!$tampoco"));
        assertFalse(PasswordHasher.verify("admin123", "otro-formato$210000$c2FsdA==$a2V5"));
        assertFalse(PasswordHasher.verify("admin123", "pbkdf2-sha256$0$c2FsdA==$a2V5"));
        assertTrue(PasswordHasher.verify("admin123", PasswordHasher.hash("admin123")));
    }

    @Test
    @DisplayName("No se permite calcular el hash de una contraseña vacía")
    void contrasenaVaciaEnHash() {
        assertThrows(IllegalArgumentException.class, () -> PasswordHasher.hash(null));
        assertThrows(IllegalArgumentException.class, () -> PasswordHasher.hash(""));
    }
}
