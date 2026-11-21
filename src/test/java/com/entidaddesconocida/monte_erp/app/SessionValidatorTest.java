package com.entidaddesconocida.monte_erp.app;

import com.entidaddesconocida.monte_erp.domain.model.Role;
import com.entidaddesconocida.monte_erp.domain.model.User;
import com.entidaddesconocida.monte_erp.viewmodel.support.FakeUserRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionValidatorTest {

    @Test
    @DisplayName("Actualiza el rol de una sesión activa desde el repositorio")
    void refreshUpdatesTheSessionSnapshot() throws Exception {
        User current = new User("Ana", "ana", "hash", Role.ADMIN, true);
        FakeUserRepository repository = new FakeUserRepository().withUser(current);
        SessionState session = new SessionState();
        session.signIn(current);
        User demoted = new User("Ana", "ana", "hash", Role.EMPLOYEE, true);
        demoted.setId(current.getId());
        repository.update(demoted);

        assertTrue(new SessionValidator(session, repository).refresh());
        assertFalse(session.currentUser().isAdmin());
        assertTrue(session.currentUser().active());
        // The snapshot is replaced, never the entity that started the session,
        // so no credential or shared state leaks into the screens (S3).
        assertTrue(current.isAdmin());
    }

    @Test
    @DisplayName("Cierra una sesión cuyo usuario fue desactivado")
    void refreshSignsOutDeactivatedUser() throws Exception {
        User current = new User("Bea", "bea", "hash", Role.EMPLOYEE, true);
        FakeUserRepository repository = new FakeUserRepository().withUser(current);
        SessionState session = new SessionState();
        session.signIn(current);
        repository.deactivate(current.getId());

        assertFalse(new SessionValidator(session, repository).refresh());
        assertNull(session.currentUser());
    }

    @Test
    @DisplayName("currentUser expone el snapshot vigente, también sin sesión")
    void currentUserExposesTheLiveSnapshot() {
        SessionState session = new SessionState();
        SessionValidator validator = new SessionValidator(session, new FakeUserRepository());
        assertNull(validator.currentUser());

        session.signIn(new User("Ana", "ana", "hash", Role.ADMIN, true));

        assertEquals("ana", validator.currentUser().username());
    }

    @Test
    @DisplayName("synchronize solo aplica los cambios de la cuenta firmada")
    void synchronizeOnlyAppliesToTheSignedInAccount() {
        User current = new User("Ana", "ana", "hash", Role.ADMIN, true);
        FakeUserRepository repository = new FakeUserRepository().withUser(current);
        SessionState session = new SessionState();
        session.signIn(current);
        SessionValidator validator = new SessionValidator(session, repository);

        User renamed = new User("Ana María", "ana", "hash", Role.ADMIN, true);
        renamed.setId(current.getId());
        validator.synchronize(renamed);
        assertEquals("Ana María", session.currentUser().name());

        User other = new User("Otra", "otra", "hash", Role.ADMIN, true);
        other.setId(current.getId() + 1);
        validator.synchronize(other);
        assertEquals("Ana María", session.currentUser().name());
    }
}
