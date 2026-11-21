package com.entidaddesconocida.monte_erp.app;

import com.entidaddesconocida.monte_erp.domain.model.Role;
import com.entidaddesconocida.monte_erp.domain.model.User;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link SessionState}: the "no main screen without a signed-in
 * session" rule, checked without the JavaFX toolkit.
 */
class SessionStateTest {

    @Test
    @DisplayName("Sin sesión, currentUser es null y requireCurrentUser falla")
    void withoutSessionThereIsNoCurrentUser() {
        SessionState session = new SessionState();

        assertNull(session.currentUser());

        IllegalStateException ex = assertThrows(IllegalStateException.class, session::requireCurrentUser);
        assertEquals("No hay sesión iniciada", ex.getMessage());
    }

    @Test
    @DisplayName("signIn guarda un snapshot con la identidad del usuario")
    void signInStoresTheUser() {
        SessionState session = new SessionState();
        User user = new User("Ana", "ana", "hash", Role.ADMIN, true);
        user.setId(7);

        session.signIn(user);

        SessionUser current = session.currentUser();
        assertEquals(7, current.id());
        assertEquals("Ana", current.name());
        assertEquals("ana", current.username());
        assertEquals(Role.ADMIN, current.role());
        assertTrue(current.active());
        assertEquals(current, session.requireCurrentUser());
    }

    @Test
    @DisplayName("La sesión no sigue los cambios de la entidad que la inició")
    void theSessionIsASnapshotOfTheUser() {
        SessionState session = new SessionState();
        User user = new User("Ana", "ana", "hash", Role.ADMIN, true);
        session.signIn(user);

        user.setName("Ana María");
        user.setRole(Role.EMPLOYEE);

        assertEquals("Ana", session.requireCurrentUser().name());
        assertTrue(session.requireCurrentUser().isAdmin());
    }

    @Test
    @DisplayName("signOut limpia la sesión y vuelve a exigir un usuario")
    void signOutClearsTheSession() {
        SessionState session = new SessionState();
        session.signIn(new User("Ana", "ana", "hash", Role.ADMIN, true));

        session.signOut();

        assertNull(session.currentUser());
        assertThrows(IllegalStateException.class, session::requireCurrentUser);
    }
}
