package com.entidaddesconocida.monte_erp.app;

import com.entidaddesconocida.monte_erp.domain.model.Role;
import com.entidaddesconocida.monte_erp.domain.model.User;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link SessionUser}: the credential free snapshot of the
 * signed-in user.
 */
class SessionUserTest {

    @Test
    @DisplayName("of copia identidad, rol y estado, y descarta la contraseña")
    void ofCopiesTheSessionFieldsOnly() {
        User user = new User("Ana", "ana", "pbkdf2-sha256$210000$salt$key", Role.ADMIN, true);
        user.setId(7);

        SessionUser snapshot = SessionUser.of(user);

        assertEquals(7, snapshot.id());
        assertEquals("Ana", snapshot.name());
        assertEquals("ana", snapshot.username());
        assertEquals(Role.ADMIN, snapshot.role());
        assertTrue(snapshot.active());
        assertTrue(snapshot.isAdmin());
        assertEquals(Role.ADMIN.label(), snapshot.roleLabel());
    }

    @Test
    @DisplayName("of(null) no crea sesión")
    void ofNullIsNoSession() {
        assertNull(SessionUser.of(null));
    }

    @Test
    @DisplayName("Un rol sin privilegios no es administrador")
    void employeeIsNotAdmin() {
        SessionUser snapshot = SessionUser.of(
                new User("Bea", "bea", "hash", Role.EMPLOYEE, true));

        assertFalse(snapshot.isAdmin());
        assertEquals(Role.EMPLOYEE.label(), snapshot.roleLabel());
    }

    @Test
    @DisplayName("La referencia para los agregados no lleva el hash de la contraseña")
    void entityReferenceCarriesNoCredentials() {
        User persisted = new User("Bea", "bea", "pbkdf2-sha256$210000$salt$key", Role.EMPLOYEE, true);
        persisted.setId(9);

        User reference = SessionUser.of(persisted).toEntityReference();

        assertEquals(9, reference.getId());
        assertEquals("Bea", reference.getName());
        assertEquals("bea", reference.getUsername());
        assertEquals(Role.EMPLOYEE, reference.getRole());
        assertTrue(reference.isActive());
        assertNull(reference.getPasswordHash());
    }
}
