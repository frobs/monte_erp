package com.entidaddesconocida.monte_erp.app;

import com.entidaddesconocida.monte_erp.domain.model.Role;
import com.entidaddesconocida.monte_erp.domain.model.User;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    @DisplayName("signIn guarda el usuario y requireCurrentUser lo devuelve")
    void signInStoresTheUser() {
        SessionState session = new SessionState();
        User user = new User("Ana", "ana", "hash", Role.ADMIN, true);

        session.signIn(user);

        assertSame(user, session.currentUser());
        assertSame(user, session.requireCurrentUser());
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
