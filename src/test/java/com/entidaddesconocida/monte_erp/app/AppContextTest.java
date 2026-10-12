package com.entidaddesconocida.monte_erp.app;

import com.entidaddesconocida.monte_erp.domain.model.User;
import com.entidaddesconocida.monte_erp.infrastructure.config.DatabaseConfig;
import com.entidaddesconocida.monte_erp.infrastructure.config.DatabaseUrl;
import com.entidaddesconocida.monte_erp.infrastructure.config.FakeCredentialStore;
import com.entidaddesconocida.monte_erp.security.PasswordHasher;
import com.entidaddesconocida.monte_erp.view.CategoriesView;
import com.entidaddesconocida.monte_erp.view.LoginView;
import com.entidaddesconocida.monte_erp.view.MainView;
import com.entidaddesconocida.monte_erp.view.ProductsView;
import com.entidaddesconocida.monte_erp.view.SetupView;
import com.entidaddesconocida.monte_erp.view.UsersView;
import com.entidaddesconocida.monte_erp.viewmodel.CategoriesViewModel;
import com.entidaddesconocida.monte_erp.viewmodel.LoginViewModel;
import com.entidaddesconocida.monte_erp.viewmodel.MainViewModel;
import com.entidaddesconocida.monte_erp.viewmodel.ProductsViewModel;
import com.entidaddesconocida.monte_erp.viewmodel.SetupViewModel;
import com.entidaddesconocida.monte_erp.viewmodel.UsersViewModel;
import com.entidaddesconocida.monte_erp.viewmodel.support.FakeCategoryRepository;
import com.entidaddesconocida.monte_erp.viewmodel.support.FakeProductRepository;
import com.entidaddesconocida.monte_erp.viewmodel.support.FakeSetupService;
import com.entidaddesconocida.monte_erp.viewmodel.support.FakeSupplierRepository;
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

    private static AppContext context(SessionState session) {
        return context(session, new FakeUserRepository());
    }

    private static AppContext context(SessionState session, FakeUserRepository repo) {
        DatabaseConfig config = new DatabaseConfig(new FakeCredentialStore());
        FakeSetupService setup = new FakeSetupService();
        return new AppContext(session, new AppShell(session), repo,
                new FakeCategoryRepository(), new FakeSupplierRepository(), new FakeProductRepository(),
                config, setup, () -> new DatabaseUrl("jdbc:test"));
    }

    @Test
    @DisplayName("Tipo sin binding lanza IllegalArgumentException con mensaje explícito")
    void missingBindingThrowsExplicitError() {
        AppContext context = context(new SessionState());
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> context.create(Object.class));
        assertTrue(ex.getMessage().contains("No hay binding para"));
    }

    @Test
    @DisplayName("create(LoginViewModel) devuelve un VM construido con el repositorio inyectado")
    void loginViewModelIsCreatedWithRepository() {
        AppContext context = context(new SessionState());
        LoginViewModel vm = (LoginViewModel) context.create(LoginViewModel.class);
        assertNotNull(vm);
    }

    @Test
    @DisplayName("create(MainViewModel) sin sesión falla con IllegalStateException")
    void mainViewModelWithoutSessionFails() {
        AppContext context = context(new SessionState());
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> context.create(MainViewModel.class));
        assertEquals("No hay sesión iniciada", ex.getMessage());
    }

    @Test
    @DisplayName("create(MainViewModel) con sesión devuelve VM con datos del usuario")
    void mainViewModelWithSessionSucceeds() {
        SessionState session = new SessionState();
        session.signIn(new User("Ana", "ana", PasswordHasher.hash("ana123"), User.ROLE_ADMIN, true));
        AppContext context = context(session);

        MainViewModel vm = (MainViewModel) context.create(MainViewModel.class);
        assertNotNull(vm);
        assertEquals("Bienvenido/a, Ana", vm.welcomeProperty().get());
        assertEquals("ana", vm.usernameProperty().get());
        assertEquals(User.ROLE_ADMIN_LABEL, vm.roleLabelProperty().get());
        assertTrue(vm.adminProperty().get());
        assertEquals("Base de datos: jdbc:test", vm.databaseProperty().get());
    }

    @Test
    @DisplayName("create(SetupViewModel) devuelve un VM con el servicio inyectado")
    void setupViewModelIsCreatedWithService() {
        AppContext context = context(new SessionState());
        SetupViewModel vm = (SetupViewModel) context.create(SetupViewModel.class);
        assertNotNull(vm);
    }

    @Test
    @DisplayName("create(UsersViewModel) sin sesión falla con IllegalStateException")
    void usersViewModelWithoutSessionFails() {
        AppContext context = context(new SessionState());
        assertThrows(IllegalStateException.class, () -> context.create(UsersViewModel.class));
    }

    @Test
    @DisplayName("create(UsersViewModel) con sesión carga la lista del repositorio")
    void usersViewModelWithSessionSucceeds() {
        SessionState session = new SessionState();
        User admin = new User("Ana", "ana", PasswordHasher.hash("ana123"), User.ROLE_ADMIN, true);
        session.signIn(admin);
        FakeUserRepository repo = new FakeUserRepository().withUser(admin);
        AppContext context = context(session, repo);

        UsersViewModel vm = (UsersViewModel) context.create(UsersViewModel.class);
        vm.load();

        assertEquals(1, vm.users().size());
        assertEquals("Usuarios registrados: 1", vm.userCountProperty().get());
    }

    @Test
    @DisplayName("create(ProductsViewModel) devuelve un VM con los repositorios inyectados")
    void productsViewModelIsCreatedWithRepositories() {
        AppContext context = context(new SessionState());

        ProductsViewModel vm = (ProductsViewModel) context.create(ProductsViewModel.class);
        vm.load();

        assertNotNull(vm);
        assertEquals(0, vm.products().size());
        assertEquals("Productos: 0", vm.productCountProperty().get());
    }

    @Test
    @DisplayName("create(CategoriesViewModel) devuelve un VM con el repositorio de categorías inyectado")
    void categoriesViewModelIsCreatedWithRepository() {
        AppContext context = context(new SessionState());

        CategoriesViewModel vm = (CategoriesViewModel) context.create(CategoriesViewModel.class);
        vm.load();

        assertNotNull(vm);
        assertEquals(0, vm.categories().size());
        assertEquals("Categorías: 0", vm.categoryCountProperty().get());
    }

    @Test
    @DisplayName("Las vistas se crean como instancias nuevas (no null)")
    void viewsAreCreated() {
        AppContext context = context(new SessionState());
        assertNotNull(context.create(LoginView.class));
        assertNotNull(context.create(MainView.class));
        assertNotNull(context.create(SetupView.class));
        assertNotNull(context.create(UsersView.class));
        assertNotNull(context.create(ProductsView.class));
        assertNotNull(context.create(CategoriesView.class));
    }
}
