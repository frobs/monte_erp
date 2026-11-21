package com.entidaddesconocida.monte_erp;

import com.entidaddesconocida.monte_erp.infrastructure.config.DatabaseConfig;
import com.entidaddesconocida.monte_erp.infrastructure.config.DatabaseConnection;
import com.entidaddesconocida.monte_erp.infrastructure.config.FakeCredentialStore;
import com.entidaddesconocida.monte_erp.viewmodel.support.FakeSetupService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the startup decision of {@link MonteErpApplication} (A1c).
 *
 * <p>The decision runs on a virtual thread and the window paints a progress
 * screen meanwhile, so it must always resolve to a screen instead of leaving the
 * user staring at a frozen window. No JavaFX toolkit and no database are needed:
 * the configuration is a temporary properties file plus the in-memory credential
 * store, and the unreachable database is a closed local port.</p>
 *
 * <p>The login and "onboarding with the schema already migrated" outcomes depend
 * on a real MySQL, so they are covered by the UI tests
 * ({@code LoginUiTest}, {@code SetupUiTest}) against the seeded database.</p>
 */
class MonteErpApplicationTest {

    /** Port 1 is closed on any machine: the driver fails fast, with no network. */
    private static final String UNREACHABLE_URL =
            "jdbc:mysql://127.0.0.1:1/monte_erp?connectTimeout=300&socketTimeout=300";

    @TempDir
    Path directory;

    @AfterEach
    void resetConnection() {
        // firstScreen() configures the global connection factory: leave it clean.
        DatabaseConnection.configure(null, null, null);
    }

    @Test
    @DisplayName("A1c: sin fichero de configuración se abre el asistente inicial")
    void missingConfigurationOpensOnboarding() {
        DatabaseConfig config = config(null);

        assertEquals(MonteErpApplication.FirstScreen.ONBOARDING,
                MonteErpApplication.firstScreen(config, new FakeSetupService()));
        // ensureFileExists() ran on the background thread, not before the window.
        assertTrue(Files.exists(config.configFile()));
    }

    @Test
    @DisplayName("A1c: una configuración incompleta se resuelve como asistente inicial")
    void incompleteConfigurationOpensOnboarding() {
        DatabaseConfig config = config("");

        assertEquals(MonteErpApplication.FirstScreen.ONBOARDING,
                MonteErpApplication.firstScreen(config, new FakeSetupService()));
    }

    @Test
    @DisplayName("A1c: si la base de datos no responde, la decisión acaba en la pantalla de conexión")
    void unreachableDatabaseEndsOnTheConnectionScreen() {
        DatabaseConfig config = config(UNREACHABLE_URL);

        assertEquals(MonteErpApplication.FirstScreen.SETUP,
                MonteErpApplication.firstScreen(config, new FakeSetupService().withAdmin(true)));
    }

    /**
     * DatabaseConfig over a temporary properties file with the given URL
     * ({@code null} to leave the file without the key) and in-memory credentials.
     */
    private DatabaseConfig config(String url) {
        Path file = directory.resolve(DatabaseConfig.CONFIG_FILE_NAME);
        try {
            Files.writeString(file, url == null ? "" : DatabaseConfig.KEY_URL + "=" + url + "\n");
        } catch (IOException e) {
            throw new IllegalStateException("Could not write the test configuration", e);
        }
        FakeCredentialStore credentials = new FakeCredentialStore()
                .with("db.user", "erp_user")
                .with("db.password", "erp_pass");
        return new DatabaseConfig(credentials, file);
    }
}
