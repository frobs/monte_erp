package com.entidaddesconocida.monte_erp.infrastructure.config;

import com.entidaddesconocida.monte_erp.domain.model.Role;
import com.entidaddesconocida.monte_erp.domain.model.ConnectionSettings;
import com.entidaddesconocida.monte_erp.domain.model.User;
import com.entidaddesconocida.monte_erp.domain.service.SetupException;
import com.entidaddesconocida.monte_erp.infrastructure.support.FakeJdbc;
import com.entidaddesconocida.monte_erp.security.PasswordHasher;
import com.entidaddesconocida.monte_erp.viewmodel.support.FakeUserRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link DatabaseSetupService}.
 *
 * <p>{@code hasAdminUser} and {@code load} are tested with fakes only. The
 * write paths ({@code save}, {@code provision}, {@code testConnection}) are also
 * unit tested thanks to {@link FakeJdbc}, which serves the static
 * {@link DatabaseConnection} and {@link MigrationRunner} without MySQL.</p>
 */
class DatabaseSetupServiceTest {

    private static final ConnectionSettings SETTINGS =
            new ConnectionSettings(FakeJdbc.URL, "erp_user", "erp_pass");

    @TempDir
    Path tempDir;

    private FakeJdbc fake;

    @BeforeEach
    void installFakeDriver() {
        fake = new FakeJdbc().install();
    }

    @AfterEach
    void resetDatabaseConnection() {
        DatabaseConnection.configure(null, null, null);
        fake.uninstall();
    }

    private DatabaseSetupService service(FakeUserRepository repo) {
        FakeCredentialStore store = new FakeCredentialStore();
        DatabaseConfig config = new DatabaseConfig(store, tempDir.resolve("application.properties"));
        return new DatabaseSetupService(config, repo);
    }

    @Test
    @DisplayName("hasAdminUser devuelve false cuando no hay usuarios")
    void hasAdminUserReturnsFalseWhenEmpty() throws Exception {
        DatabaseSetupService service = service(new FakeUserRepository());
        assertFalse(service.hasAdminUser());
    }

    @Test
    @DisplayName("hasAdminUser devuelve false cuando solo hay empleados")
    void hasAdminUserReturnsFalseWhenOnlyEmployees() throws Exception {
        FakeUserRepository repo = new FakeUserRepository()
                .withUser(new User("Ana", "ana", PasswordHasher.hash("ana123"), Role.EMPLOYEE, true));
        assertFalse(service(repo).hasAdminUser());
    }

    @Test
    @DisplayName("hasAdminUser devuelve true cuando hay al menos un administrador")
    void hasAdminUserReturnsTrueWhenAdminExists() throws Exception {
        FakeUserRepository repo = new FakeUserRepository()
                .withUser(new User("Ana", "ana", PasswordHasher.hash("ana123"), Role.EMPLOYEE, true))
                .withUser(new User("Bob", "bob", PasswordHasher.hash("bob123"), Role.ADMIN, true));
        assertTrue(service(repo).hasAdminUser());
    }

    @Test
    @DisplayName("load delega a DatabaseConfig y devuelve los valores persistidos")
    void loadDelegatesToConfig() throws Exception {
        FakeCredentialStore store = new FakeCredentialStore()
                .with("db.user", "erp_user")
                .with("db.password", "erp_pass");
        DatabaseConfig config = new DatabaseConfig(store, tempDir.resolve("application.properties"));
        config.save(new ConnectionSettings("jdbc:mysql://localhost/monte_erp", "erp_user", "erp_pass"));

        DatabaseSetupService service = new DatabaseSetupService(config, new FakeUserRepository());
        ConnectionSettings loaded = service.load();

        assertTrue(loaded.isComplete());
        assertEquals("jdbc:mysql://localhost/monte_erp", loaded.url());
        assertEquals("erp_user", loaded.user());
        assertEquals("erp_pass", loaded.password());
    }

    @Test
    @DisplayName("save persiste la configuración y apunta la factoría a ella")
    void savePersistsAndConfiguresTheConnection() throws Exception {
        DatabaseSetupService service = service(new FakeUserRepository());

        service.save(SETTINGS);

        assertEquals(FakeJdbc.URL, DatabaseConnection.getUrl());
        assertNotNull(DatabaseConnection.open());
    }

    @Test
    @DisplayName("testConnection no lanza cuando la base de datos responde")
    void testConnectionSucceeds() throws Exception {
        service(new FakeUserRepository()).testConnection(SETTINGS);
    }

    @Test
    @DisplayName("Un fallo de conexión se envuelve en SetupException")
    void testConnectionFailureIsWrapped() {
        DatabaseSetupService service = service(new FakeUserRepository());

        SetupException ex = assertThrows(SetupException.class,
                () -> service.testConnection(new ConnectionSettings("", "erp_user", "erp_pass")));

        assertNotNull(ex.getMessage());
    }

    @Test
    @DisplayName("provision migra el esquema y crea el administrador con la contraseña hasheada")
    void provisionMigratesAndCreatesTheAdmin() throws Exception {
        FakeUserRepository repo = new FakeUserRepository();
        fake.withGeneratedKey(1);

        service(repo).provision(SETTINGS, "Administrador", "admin", "admin123");

        User admin = repo.findByUsername("admin");
        assertNotNull(admin);
        assertTrue(admin.isAdmin());
        assertTrue(admin.isActive());
        assertTrue(PasswordHasher.verify("admin123", admin.getPasswordHash()));
    }

    @Test
    @DisplayName("provision es idempotente si el administrador ya existe")
    void provisionIsIdempotentForAnExistingAdmin() throws Exception {
        FakeUserRepository repo = new FakeUserRepository()
                .withUser(new User("Administrador", "admin", PasswordHasher.hash("admin123"), Role.ADMIN, true));
        DatabaseSetupService service = service(repo);

        service.provision(SETTINGS, "Otro", "admin", "otra123");

        assertEquals(1, repo.findAll().size());
        assertTrue(PasswordHasher.verify("admin123", repo.findByUsername("admin").getPasswordHash()));
    }

    @Test
    @DisplayName("provision rechaza un nombre de usuario ocupado por un empleado")
    void provisionRejectsUsernameTakenByAnEmployee() throws Exception {
        FakeUserRepository repo = new FakeUserRepository()
                .withUser(new User("Bea", "admin", PasswordHasher.hash("bea123"), Role.EMPLOYEE, true));

        SetupException ex = assertThrows(SetupException.class,
                () -> service(repo).provision(SETTINGS, "Administrador", "admin", "admin123"));

        assertTrue(ex.getMessage().contains("ya existe"));
        assertFalse(repo.findByUsername("admin").isAdmin());
    }

    @Test
    @DisplayName("Los mensajes de error ocultan rutas e IPs antes de mostrarse")
    void errorMessagesHidePathsAndAddresses() {
        CredentialStore failing = new CredentialStore() {
            @Override
            public String get(String account) {
                return null;
            }

            @Override
            public void set(String account, String value) {
                throw new CredentialStoreException("Failed at C:\\Users\\admin\\keyring and 192.168.1.10:3306");
            }

            @Override
            public void delete(String account) {
                // No credentials exist in this failing test double.
            }
        };
        DatabaseConfig config = new DatabaseConfig(failing, tempDir.resolve("application.properties"));
        DatabaseSetupService service = new DatabaseSetupService(config, new FakeUserRepository());

        SetupException ex = assertThrows(SetupException.class, () -> service.save(SETTINGS));

        assertTrue(ex.getMessage().contains("[ruta]"), ex.getMessage());
        assertTrue(ex.getMessage().contains("[red]"), ex.getMessage());
        assertFalse(ex.getMessage().contains("192.168.1.10"), ex.getMessage());
        assertFalse(ex.getMessage().contains("Users"), ex.getMessage());
    }
}
