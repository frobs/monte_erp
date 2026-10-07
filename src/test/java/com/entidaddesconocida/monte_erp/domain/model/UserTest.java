package com.entidaddesconocida.monte_erp.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link User}: the user facing labels and the role/status
 * helpers used by the views.
 */
class UserTest {

    @Test
    @DisplayName("isAdmin y getRoleLabel distinguen administrador y empleado")
    void roleLabelsFollowTheRole() {
        User admin = new User("Ana", "ana", "hash", User.ROLE_ADMIN, true);
        User employee = new User("Bea", "bea", "hash", User.ROLE_EMPLOYEE, true);
        User unknown = new User("Cris", "cris", "hash", null, true);

        assertTrue(admin.isAdmin());
        assertEquals(User.ROLE_ADMIN_LABEL, admin.getRoleLabel());

        assertFalse(employee.isAdmin());
        assertEquals(User.ROLE_EMPLOYEE_LABEL, employee.getRoleLabel());

        // Any role that is not admin is presented as employee.
        assertFalse(unknown.isAdmin());
        assertEquals(User.ROLE_EMPLOYEE_LABEL, unknown.getRoleLabel());
    }

    @Test
    @DisplayName("getStatusLabel distingue activo e inactivo")
    void statusLabelsFollowTheFlag() {
        assertEquals(User.ACTIVE_LABEL, new User("Ana", "ana", "hash", User.ROLE_ADMIN, true).getStatusLabel());
        assertEquals(User.INACTIVE_LABEL, new User("Bea", "bea", "hash", User.ROLE_EMPLOYEE, false).getStatusLabel());
    }

    @Test
    @DisplayName("El constructor sin argumentos y los setters reconstruyen la entidad")
    void settersRebuildTheEntity() {
        User user = new User();
        user.setId(9);
        user.setName("Ana");
        user.setUsername("ana");
        user.setPasswordHash("hash");
        user.setRole(User.ROLE_ADMIN);
        user.setActive(true);

        assertEquals(9, user.getId());
        assertEquals("Ana", user.getName());
        assertEquals("ana", user.getUsername());
        assertEquals("hash", user.getPasswordHash());
        assertEquals(User.ROLE_ADMIN, user.getRole());
        assertTrue(user.isActive());

        user.setActive(false);
        assertFalse(user.isActive());
    }

    @Test
    @DisplayName("toString resume id, usuario, rol y estado")
    void toStringSummarizesTheUser() {
        User user = new User("Ana", "ana", "hash", User.ROLE_ADMIN, true);
        user.setId(4);

        String text = user.toString();

        assertTrue(text.contains("id=4"));
        assertTrue(text.contains("username='ana'"));
        assertTrue(text.contains("role='admin'"));
        assertTrue(text.contains("active=true"));
    }
}
