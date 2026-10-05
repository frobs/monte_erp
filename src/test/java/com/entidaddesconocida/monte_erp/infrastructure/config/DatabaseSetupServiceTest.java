package com.entidaddesconocida.monte_erp.infrastructure.config;

import com.entidaddesconocida.monte_erp.domain.model.ConnectionSettings;
import com.entidaddesconocida.monte_erp.domain.model.User;
import com.entidaddesconocida.monte_erp.security.PasswordHasher;
import com.entidaddesconocida.monte_erp.viewmodel.support.FakeUserRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link DatabaseSetupService}.
 *
 * <p>Only {@link #hasAdminUser()} and {@link #load()} are tested here because
 * the other methods ({@code save}, {@code provision}, {@code testConnection})
 * depend on the static {@link DatabaseConnection} and {@link MigrationRunner},
 * which makes them integration tests rather than unit tests.</p>
 */
class DatabaseSetupServiceTest {

    @TempDir
    Path tempDir;

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
                .withUser(new User("Ana", "ana", PasswordHasher.hash("ana123"), User.ROLE_EMPLOYEE, true));
        assertFalse(service(repo).hasAdminUser());
    }

    @Test
    @DisplayName("hasAdminUser devuelve true cuando hay al menos un administrador")
    void hasAdminUserReturnsTrueWhenAdminExists() throws Exception {
        FakeUserRepository repo = new FakeUserRepository()
                .withUser(new User("Ana", "ana", PasswordHasher.hash("ana123"), User.ROLE_EMPLOYEE, true))
                .withUser(new User("Bob", "bob", PasswordHasher.hash("bob123"), User.ROLE_ADMIN, true));
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
}
