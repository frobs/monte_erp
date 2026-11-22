package com.entidaddesconocida.monte_erp.app;

import com.entidaddesconocida.monte_erp.domain.model.Role;
import com.entidaddesconocida.monte_erp.domain.model.User;
import com.entidaddesconocida.monte_erp.viewmodel.support.FakeUserRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionGuardTest {

    private static final String DB_ERROR = "No se pudo conectar con la base de datos.";
    private static final String EXPIRED = "La sesión ya no está activa. Inicia sesión de nuevo.";
    private static final Logger LOGGER = Logger.getLogger(SessionGuardTest.class.getName());

    /** Records the cleanup calls and the messages shown, instead of touching JavaFX. */
    private static final class Screen {
        private final List<String> calls = new ArrayList<>();
        private final List<String> messages = new ArrayList<>();

        void clear() {
            calls.add("clear");
        }

        void fail(String message) {
            messages.add(message);
        }
    }

    private static SessionGuard guard(SessionValidator validator) {
        return new SessionGuard(LOGGER, validator, DB_ERROR, EXPIRED);
    }

    @Test
    @DisplayName("Sin validador la acción sigue adelante y no toca el estado")
    void withoutValidatorTheActionRuns() {
        Screen screen = new Screen();

        assertTrue(guard(null).requireActive(screen::clear, screen::fail));
        assertEquals(List.of(), screen.calls);
        assertEquals(List.of(), screen.messages);
    }

    @Test
    @DisplayName("Con la sesión viva la acción sigue adelante y no limpia nada")
    void activeSessionKeepsTheScreen() {
        User ana = new User("Ana", "ana", "hash", Role.ADMIN, true);
        FakeUserRepository users = new FakeUserRepository().withUser(ana);
        SessionState session = new SessionState();
        session.signIn(ana);
        Screen screen = new Screen();

        assertTrue(guard(new SessionValidator(session, users))
                .requireActive(screen::clear, screen::fail));

        assertEquals(List.of(), screen.calls);
        assertEquals(List.of(), screen.messages);
    }

    @Test
    @DisplayName("Una cuenta desactivada limpia la pantalla, avisa y cierra la sesión")
    void deactivatedAccountClearsAndSignsOut() throws Exception {
        User ana = new User("Ana", "ana", "hash", Role.ADMIN, true);
        FakeUserRepository users = new FakeUserRepository().withUser(ana);
        SessionState session = new SessionState();
        session.signIn(ana);
        users.deactivate(ana.getId());
        Screen screen = new Screen();

        boolean active = guard(new SessionValidator(session, users))
                .requireActive(screen::clear, screen::fail);

        assertFalse(active);
        assertEquals(List.of("clear"), screen.calls);
        assertEquals(List.of(EXPIRED), screen.messages);
        assertNull(session.currentUser());
    }

    @Test
    @DisplayName("Una caída de la base de datos avisa pero conserva el formulario")
    void databaseFailureKeepsTheScreenByDefault() {
        User ana = new User("Ana", "ana", "hash", Role.ADMIN, true);
        SessionState session = new SessionState();
        session.signIn(ana);
        FakeUserRepository users = new FakeUserRepository().withUser(ana).throwOnAccess();
        Screen screen = new Screen();

        boolean active = guard(new SessionValidator(session, users))
                .requireActive(screen::clear, screen::fail);

        assertFalse(active);
        assertEquals(List.of(), screen.calls);
        assertEquals(List.of(DB_ERROR), screen.messages);
    }

    @Test
    @DisplayName("El informe opta por vaciarse también cuando la comprobación falla por la base")
    void readOnlyScreensCanClearOnDatabaseFailure() {
        User ana = new User("Ana", "ana", "hash", Role.ADMIN, true);
        SessionState session = new SessionState();
        session.signIn(ana);
        FakeUserRepository users = new FakeUserRepository().withUser(ana).throwOnAccess();
        Screen screen = new Screen();

        boolean active = guard(new SessionValidator(session, users))
                .requireActive(screen::clear, screen::clear, screen::fail);

        assertFalse(active);
        assertEquals(List.of("clear"), screen.calls);
        assertEquals(List.of(DB_ERROR), screen.messages);
    }
}
