package com.entidaddesconocida.monte_erp.infrastructure.config;

import com.entidaddesconocida.monte_erp.domain.model.ConnectionSettings;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link DatabaseConfig}: URL persistence in the properties file
 * and credential delegation to a fake {@link CredentialStore} (no real keyring).
 */
class DatabaseConfigTest {

    private static final String ACCOUNT_USER = "db.user";
    private static final String ACCOUNT_PASSWORD = "db.password";

    @TempDir
    Path tempDir;

    private DatabaseConfig config(FakeCredentialStore store) {
        return new DatabaseConfig(store, tempDir.resolve("application.properties"));
    }

    @Test
    @DisplayName("ensureFileExists crea el archivo cuando no existe")
    void ensureFileExistsCreatesFile() throws IOException {
        Path file = tempDir.resolve("application.properties");
        FakeCredentialStore store = new FakeCredentialStore();
        assertFalse(Files.exists(file));
        config(store).ensureFileExists();
        assertTrue(Files.isRegularFile(file));
    }

    @Test
    @DisplayName("save escribe la URL en el archivo y las credenciales en el keyring")
    void savePersistsUrlAndCredentials() throws IOException {
        FakeCredentialStore store = new FakeCredentialStore();
        DatabaseConfig config = config(store);
        config.save(new ConnectionSettings("jdbc:mysql://localhost/monte_erp", "erp_user", "erp_pass"));

        Properties properties = new Properties();
        try (var in = Files.newInputStream(config.configFile())) {
            properties.load(in);
        }
        assertEquals("jdbc:mysql://localhost/monte_erp", properties.getProperty(DatabaseConfig.KEY_URL));
        assertEquals("erp_user", store.stored(ACCOUNT_USER));
        assertEquals("erp_pass", store.stored(ACCOUNT_PASSWORD));
    }

    @Test
    @DisplayName("load devuelve los valores persistidos")
    void loadReturnsPersistedValues() throws IOException {
        FakeCredentialStore store = new FakeCredentialStore()
                .with(ACCOUNT_USER, "erp_user")
                .with(ACCOUNT_PASSWORD, "erp_pass");
        DatabaseConfig config = config(store);
        config.save(new ConnectionSettings("jdbc:mysql://localhost/monte_erp", "erp_user", "erp_pass"));

        ConnectionSettings loaded = config.load();
        assertTrue(loaded.isComplete());
        assertEquals("jdbc:mysql://localhost/monte_erp", loaded.url());
        assertEquals("erp_user", loaded.user());
        assertEquals("erp_pass", loaded.password());
    }

    @Test
    @DisplayName("load con configuración ausente devuelve valores null")
    void loadReturnsNullsWhenMissing() {
        ConnectionSettings loaded = config(new FakeCredentialStore()).load();
        assertNull(loaded.url());
        assertNull(loaded.user());
        assertNull(loaded.password());
        assertFalse(loaded.isComplete());
    }

    @Test
    @DisplayName("save conserva el resto de claves del archivo de propiedades")
    void savePreservesOtherKeys() throws IOException {
        Path file = tempDir.resolve("application.properties");
        Properties seed = new Properties();
        seed.setProperty("otra.clave", "valor");
        try (var out = Files.newOutputStream(file)) {
            seed.store(out, null);
        }

        FakeCredentialStore store = new FakeCredentialStore();
        config(store).save(new ConnectionSettings("jdbc:mysql://localhost/monte_erp", "erp_user", "erp_pass"));

        Properties properties = new Properties();
        try (var in = Files.newInputStream(file)) {
            properties.load(in);
        }
        assertEquals("valor", properties.getProperty("otra.clave"));
        assertEquals("jdbc:mysql://localhost/monte_erp", properties.getProperty(DatabaseConfig.KEY_URL));
    }
}
