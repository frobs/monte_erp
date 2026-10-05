package com.entidaddesconocida.monte_erp.app;

import com.entidaddesconocida.monte_erp.domain.model.User;
import com.entidaddesconocida.monte_erp.infrastructure.config.DatabaseConfig;
import com.entidaddesconocida.monte_erp.infrastructure.config.DatabaseUrl;
import com.entidaddesconocida.monte_erp.infrastructure.config.FakeCredentialStore;
import com.entidaddesconocida.monte_erp.security.PasswordHasher;
import com.entidaddesconocida.monte_erp.view.LoginView;
import com.entidaddesconocida.monte_erp.view.MainView;
import com.entidaddesconocida.monte_erp.view.SetupView;
import com.entidaddesconocida.monte_erp.viewmodel.LoginViewModel;
import com.entidaddesconocida.monte_erp.viewmodel.MainViewModel;
import com.entidaddesconocida.monte_erp.viewmodel.SetupViewModel;
import com.entidaddesconocida.monte_erp.viewmodel.support.FakeSetupService;
import com.entidaddesconocida.monte_erp.viewmodel.support.FakeUserRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the composition root {@link AppContext}.
 *
 * <p>No JavaFX toolkit: only the container wiring and the session rule are
 * checked.</p>
 */
class AppContextTest {

    private static AppContext context(SessionState session, AppShell shell) {
        FakeUserRepository repo = new FakeUserRepository();
        DatabaseConfig config = new DatabaseConfig(new FakeCredentialStore());
        FakeSetupService setup = new FakeSetupService();
        return new AppContext(session, shell, repo, config, setup, () -> new DatabaseUrl("jdbc:test"));
    }

    @Test
    @DisplayName("Tipo sin binding lanza IllegalArgumentException con mensaje explícito")
    void missingBindingThrowsExplicitError() {
        AppContext context = context(new SessionState(), new AppShell(new SessionState()));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> context.create(Object.class));
        assertTrue(ex.getMessage().contains("No hay binding para"));
    }

    @Test
    @DisplayName("create(LoginViewModel) devuelve un VM construido con el repositorio inyectado")
    void loginViewModelIsCreatedWithRepository() {
        AppContext context = context(new SessionState(), new AppShell(new SessionState()));
        LoginViewModel vm = (LoginViewModel) context.create(LoginViewModel.class);
        assertNotNull(vm);
    }

    @Test
    @DisplayName("create(MainViewModel) sin sesión falla con IllegalStateException")
    void mainViewModelWithoutSessionFails() {
        AppContext context = context(new SessionState(), new AppShell(new SessionState()));
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> context.create(MainViewModel.class));
        assertEquals("No hay sesión iniciada", ex.getMessage());
    }

    @Test
    @DisplayName("create(MainViewModel) con sesión devuelve VM con datos del usuario")
    void mainViewModelWithSessionSucceeds() {
        SessionState session = new SessionState();
        session.signIn(new User("Ana", "ana", PasswordHasher.hash("ana123"), User.ROLE_ADMIN, true));
        AppContext context = context(session, new AppShell(session));

        MainViewModel vm = (MainViewModel) context.create(MainViewModel.class);
        assertNotNull(vm);
        assertEquals("Bienvenido/a, Ana", vm.welcomeProperty().get());
        assertTrue(vm.adminProperty().get());
        assertEquals("Base de datos: jdbc:test", vm.databaseProperty().get());
    }

    @Test
    @DisplayName("create(SetupViewModel) devuelve un VM con el servicio inyectado")
    void setupViewModelIsCreatedWithService() {
        AppContext context = context(new SessionState(), new AppShell(new SessionState()));
        SetupViewModel vm = (SetupViewModel) context.create(SetupViewModel.class);
        assertNotNull(vm);
    }

    @Test
    @DisplayName("Las vistas se crean como instancias nuevas (no null)")
    void viewsAreCreated() {
        AppContext context = context(new SessionState(), new AppShell(new SessionState()));
        assertNotNull(context.create(LoginView.class));
        assertNotNull(context.create(MainView.class));
        assertNotNull(context.create(SetupView.class));
    }
}
