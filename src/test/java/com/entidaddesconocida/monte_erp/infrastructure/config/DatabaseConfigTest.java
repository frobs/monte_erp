package com.entidaddesconocida.monte_erp.infrastructure.config;

import com.entidaddesconocida.monte_erp.domain.model.ConnectionSettings;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    @DisplayName("save escribe el archivo en UTF-8 con un comentario ASCII (S5)")
    void saveWritesUtf8WithoutNonAsciiBytes() throws IOException {
        DatabaseConfig config = config(new FakeCredentialStore());

        config.save(new ConnectionSettings("jdbc:mysql://localhost/monte_erp", "erp_user", "erp_pass"));

        byte[] bytes = Files.readAllBytes(config.configFile());
        String text = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
        for (byte b : bytes) {
            assertTrue(b >= 0, "byte no ASCII en el archivo: 0x" + Integer.toHexString(b & 0xFF));
        }
        assertTrue(text.contains("Monte ERP database connection URL"), text);
        assertEquals("jdbc:mysql://localhost/monte_erp", config.load().url());
    }

    @Test
    @DisplayName("save reescribe en UTF-8 un archivo legado ISO-8859-1 sin perder claves")
    void saveMigratesLegacyIsoFile() throws IOException {
        Path file = tempDir.resolve("application.properties");
        Files.write(file, ("# Monte ERP \u00b7 database connection URL\n"
                + "otra.clave=valor\n"
                + "db.url=jdbc:mysql://viejo:3306/monte_erp\n").getBytes(StandardCharsets.ISO_8859_1));

        config(new FakeCredentialStore())
                .save(new ConnectionSettings("jdbc:mysql://localhost/monte_erp", "erp_user", "erp_pass"));

        byte[] bytes = Files.readAllBytes(file);
        for (byte b : bytes) {
            assertTrue(b >= 0, "byte no ASCII tras migrar: 0x" + Integer.toHexString(b & 0xFF));
        }
        Properties properties = new Properties();
        try (var in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(in);
        }
        assertEquals("valor", properties.getProperty("otra.clave"));
        assertEquals("jdbc:mysql://localhost/monte_erp", properties.getProperty(DatabaseConfig.KEY_URL));
    }

    @Test
    @DisplayName("ensureFileExists crea los directorios intermedios del directorio de configuración")
    void ensureFileExistsCreatesParentDirectories() throws IOException {
        Path file = tempDir.resolve("nested/config/application.properties");
        DatabaseConfig config = new DatabaseConfig(new FakeCredentialStore(), file);

        config.ensureFileExists();

        assertTrue(Files.isRegularFile(file));
    }

    @Test
    @DisplayName("El constructor por defecto acepta un directorio de configuración explícito")
    void defaultConstructorUsesExplicitConfigDirectory() {
        String previous = System.getProperty(DatabaseConfig.CONFIG_DIR_PROPERTY);
        try {
            System.setProperty(DatabaseConfig.CONFIG_DIR_PROPERTY, tempDir.toString());

            DatabaseConfig config = new DatabaseConfig(new FakeCredentialStore());

            assertEquals(tempDir.resolve(DatabaseConfig.CONFIG_FILE_NAME), config.configFile());
        } finally {
            if (previous == null) {
                System.clearProperty(DatabaseConfig.CONFIG_DIR_PROPERTY);
            } else {
                System.setProperty(DatabaseConfig.CONFIG_DIR_PROPERTY, previous);
            }
        }
    }

    @Test
    @DisplayName("Un directorio de configuración vacío resuelve al directorio del usuario")
    void blankConfigDirectoryUsesPerUserDirectory() {
        String previous = System.getProperty(DatabaseConfig.CONFIG_DIR_PROPERTY);
        try {
            System.setProperty(DatabaseConfig.CONFIG_DIR_PROPERTY, "");

            Path file = DatabaseConfig.defaultConfigFile();

            assertTrue(file.isAbsolute());
            assertEquals(DatabaseConfig.CONFIG_FILE_NAME, file.getFileName().toString());
            assertFalse(file.equals(Path.of(DatabaseConfig.CONFIG_FILE_NAME).toAbsolutePath()));
        } finally {
            if (previous == null) {
                System.clearProperty(DatabaseConfig.CONFIG_DIR_PROPERTY);
            } else {
                System.setProperty(DatabaseConfig.CONFIG_DIR_PROPERTY, previous);
            }
        }
    }

    @Test
    @DisplayName("ensureFileExists es idempotente si el archivo ya existe")
    void ensureFileExistsIsIdempotent() throws IOException {
        Path file = tempDir.resolve("application.properties");
        DatabaseConfig config = config(new FakeCredentialStore());

        config.ensureFileExists();
        config.ensureFileExists();

        assertTrue(Files.isRegularFile(file));
    }

    @Test
    @DisplayName("save recorta espacios de la URL antes de persistirla")
    void saveTrimsTheUrl() throws IOException {
        DatabaseConfig config = config(new FakeCredentialStore());

        config.save(new ConnectionSettings("  jdbc:mysql://localhost/monte_erp  ", "erp_user", "erp_pass"));

        assertEquals("jdbc:mysql://localhost/monte_erp", config.load().url());
    }

    @Test
    @DisplayName("save con URL null escribe vacío y load devuelve null")
    void saveWithNullUrlPersistsAnEmptyValue() throws IOException {
        DatabaseConfig config = config(new FakeCredentialStore());

        config.save(new ConnectionSettings(null, "erp_user", "erp_pass"));

        assertNull(config.load().url());
    }

    @Test
    @DisplayName("load trata las credenciales en blanco del keyring como ausentes")
    void loadTreatsBlankCredentialsAsMissing() {
        FakeCredentialStore store = new FakeCredentialStore()
                .with(ACCOUNT_USER, "   ")
                .with(ACCOUNT_PASSWORD, "");

        ConnectionSettings loaded = config(store).load();

        assertNull(loaded.user());
        assertNull(loaded.password());
        assertFalse(loaded.isComplete());
    }

    @Test
    @DisplayName("save rechaza credenciales embebidas en la URL sin escribir nada")
    void saveRejectsSecretsInUrl() {
        FakeCredentialStore store = new FakeCredentialStore();
        DatabaseConfig config = config(store);

        assertThrows(IllegalArgumentException.class, () -> config.save(new ConnectionSettings(
                "jdbc:mysql://localhost/monte_erp?password=secret", "erp_user", "erp_pass")));

        assertNull(config.load().url());
        assertNull(store.stored(ACCOUNT_USER));
        assertNull(store.stored(ACCOUNT_PASSWORD));
    }

    @Test
    @DisplayName("Un fallo del keyring al guardar se propaga como CredentialStoreException")
    void savePropagatesKeyringFailure() {
        DatabaseConfig config = config(new FakeCredentialStore().throwOnSet());

        assertThrows(CredentialStoreException.class,
                () -> config.save(new ConnectionSettings("jdbc:mysql://localhost/monte_erp", "erp_user", "erp_pass")));
    }

    @Test
    @DisplayName("load distingue falta de credencial de backend no disponible")
    void loadDistinguishesMissingFromUnavailable() {
        DatabaseConfig missing = config(new FakeCredentialStore());
        DatabaseConfig.LoadedConfiguration absent = missing.loadWithStatus();
        assertFalse(absent.credentialsUnavailable());
        assertNull(absent.settings().user());

        DatabaseConfig unreadable = config(new FakeCredentialStore().throwOnGet());
        DatabaseConfig.LoadedConfiguration unavailable = unreadable.loadWithStatus();
        assertTrue(unavailable.credentialsUnavailable());
        assertNull(unavailable.settings().user());
        assertNull(unavailable.settings().password());
    }

    @Test
    @DisplayName("Un fallo de lectura de una sola credencial marca el keyring como no disponible")
    void oneUnreadableCredentialMarksTheKeyringUnavailable() {
        FakeCredentialStore store = new FakeCredentialStore()
                .with(ACCOUNT_USER, "erp_user")
                .with(ACCOUNT_PASSWORD, "erp_pass")
                .failOnGet(ACCOUNT_PASSWORD);

        DatabaseConfig.LoadedConfiguration loaded = config(store).loadWithStatus();

        assertTrue(loaded.credentialsUnavailable());
        assertEquals("erp_user", loaded.settings().user());
        assertNull(loaded.settings().password());
    }

    @Test
    @DisplayName("save no borra las credenciales anteriores si no se pudieron leer")
    void saveKeepsCredentialsWhenPreviousValueIsUnknown() {
        FakeCredentialStore store = new FakeCredentialStore()
                .with(ACCOUNT_USER, "old_user")
                .with(ACCOUNT_PASSWORD, "old_password")
                .throwOnGet()
                .failOnSet(ACCOUNT_PASSWORD);
        DatabaseConfig config = config(store);

        assertThrows(CredentialStoreException.class, () -> config.save(new ConnectionSettings(
                "jdbc:mysql://new-host/monte_erp", "new_user", "new_password")));

        // The previous password could not be read, so the rollback leaves it alone
        // instead of deleting a credential that may still be valid.
        assertEquals("old_password", store.stored(ACCOUNT_PASSWORD));
    }

    @Test
    @DisplayName("Un fallo parcial del keyring restaura las credenciales y la URL anteriores")
    void saveRollsBackCredentialsWhenKeyringUpdateFails() throws IOException {
        FakeCredentialStore store = new FakeCredentialStore()
                .with(ACCOUNT_USER, "old_user")
                .with(ACCOUNT_PASSWORD, "old_password");
        DatabaseConfig config = config(store);
        ConnectionSettings previous = new ConnectionSettings(
                "jdbc:mysql://old-host/monte_erp", "old_user", "old_password");
        config.save(previous);
        store.failOnSet(ACCOUNT_PASSWORD);

        assertThrows(CredentialStoreException.class, () -> config.save(new ConnectionSettings(
                "jdbc:mysql://new-host/monte_erp", "new_user", "new_password")));

        assertEquals(previous, config.load());
        assertEquals("old_user", store.stored(ACCOUNT_USER));
        assertEquals("old_password", store.stored(ACCOUNT_PASSWORD));
    }
}
