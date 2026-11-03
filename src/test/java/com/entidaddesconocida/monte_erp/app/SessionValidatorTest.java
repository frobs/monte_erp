package com.entidaddesconocida.monte_erp.app;

import com.entidaddesconocida.monte_erp.domain.model.User;
import com.entidaddesconocida.monte_erp.viewmodel.support.FakeUserRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionValidatorTest {

    @Test
    @DisplayName("Actualiza el rol de una sesión activa desde el repositorio")
    void refreshUpdatesCurrentUserRole() throws Exception {
        User current = new User("Ana", "ana", "hash", User.ROLE_ADMIN, true);
        FakeUserRepository repository = new FakeUserRepository().withUser(current);
        SessionState session = new SessionState();
        session.signIn(current);
        User demoted = new User("Ana", "ana", "hash", User.ROLE_EMPLOYEE, true);
        demoted.setId(current.getId());
        repository.update(demoted);

        assertTrue(new SessionValidator(session, repository).refresh());
        assertFalse(current.isAdmin());
        assertTrue(current.isActive());
    }

    @Test
    @DisplayName("Cierra una sesión cuyo usuario fue desactivado")
    void refreshSignsOutDeactivatedUser() throws Exception {
        User current = new User("Bea", "bea", "hash", User.ROLE_EMPLOYEE, true);
        FakeUserRepository repository = new FakeUserRepository().withUser(current);
        SessionState session = new SessionState();
        session.signIn(current);
        repository.deactivate(current.getId());

        assertFalse(new SessionValidator(session, repository).refresh());
        assertNull(session.currentUser());
    }
}
