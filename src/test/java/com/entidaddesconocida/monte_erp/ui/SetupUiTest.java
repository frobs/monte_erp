package com.entidaddesconocida.monte_erp.ui;

import com.entidaddesconocida.monte_erp.infrastructure.config.FakeCredentialStore;
import com.entidaddesconocida.monte_erp.ui.support.FxRunner;
import com.entidaddesconocida.monte_erp.ui.support.TempFiles;
import com.entidaddesconocida.monte_erp.ui.support.TestDatabase;
import com.entidaddesconocida.monte_erp.ui.support.UiActions;
import com.entidaddesconocida.monte_erp.ui.support.UiTestSupport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import javafx.stage.Stage;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CU-01 (onboarding): first-run configuration of the database and the
 * administrator user, and later changes of the connection from the login
 * screen.
 */
@Tag("ui")
class SetupUiTest {

    private static final String SCHEMA = "monte_erp_onboarding";

    private Stage stage;
    private UiTestSupport app;
    private UiActions ui;

    @BeforeAll
    static void prepare() {
        TestDatabase.provision();
        FxRunner.ensureToolkitStarted();
    }

    @BeforeEach
    void createEmptySchema() {
        TestDatabase.createSchema(SCHEMA);
        TestDatabase.configureConnection();
    }

    @AfterEach
    void closeWindow() {
        if (stage != null) {
            FxRunner.run(stage::close);
        }
    }

    @Test
    @DisplayName("Onboarding: prueba de conexión, migraciones, administrador y login")
    void onboardingCreatesTheAdministrator() throws Exception {
        Path properties = TempFiles.propertiesFile("onboarding.properties",
                TestDatabase.jdbcUrl(SCHEMA));
        FakeCredentialStore credentials = new FakeCredentialStore();
        openSetup(true, properties, credentials);

        ui.assertTitle("Monte ERP · Configuración inicial");
        ui.assertVisible("adminSection", true);
        ui.assertTrue("La URL debe estar precargada",
                ui.textOf("urlField").equals(TestDatabase.jdbcUrl(SCHEMA)));

        // Without database credentials nothing can be saved.
        ui.clickButton("Guardar y continuar");
        ui.assertLabel("errorLabel", "Introduce la URL, el usuario y la contraseña.");

        ui.type("dbUserField", TestDatabase.username());
        ui.type("dbPasswordField", TestDatabase.password());
        ui.clickButton("Probar conexión");
        ui.assertLabel("statusLabel", "Conexión correcta.");

        ui.type("adminNameField", "Admin QA");
        ui.type("adminUsernameField", "admin.qa");
        ui.type("adminPasswordField", "Qa1234");
        ui.clickButton("Guardar y continuar");

        // The database is migrated and the administrator created: login shows up.
        ui.assertTitle("Monte ERP · Iniciar sesión");
        TestDatabase.configureConnection(TestDatabase.jdbcUrl(SCHEMA));
        assertEquals("admin.qa", TestDatabase.stringValue(
                "SELECT username FROM USER WHERE username = 'admin.qa'"));
        assertEquals(TestDatabase.username(), credentials.stored("db.user"));
        assertTrue(Files.readString(properties, java.nio.charset.StandardCharsets.ISO_8859_1)
                .contains("db.url"));

        // The brand new administrator can sign in and has the admin menu.
        ui.type("usernameField", "admin.qa");
        ui.type("passwordField", "Qa1234");
        ui.click("loginButton");
        ui.assertTitle("Monte ERP · Admin QA (Administrador)");
        ui.assertVisible("manageUsersButton", true);
    }

    @Test
    @DisplayName("Cambiar la conexión: valida credenciales y guarda la configuración")
    void changeConnectionValidatesAndSaves() {
        Path properties = TempFiles.propertiesFile("existing.properties",
                TestDatabase.jdbcUrl(SCHEMA));
        FakeCredentialStore credentials = new FakeCredentialStore()
                .with("db.user", TestDatabase.username())
                .with("db.password", TestDatabase.password());
        openSetup(false, properties, credentials);

        ui.assertTitle("Monte ERP · Configuración de la base de datos");
        ui.assertVisible("adminSection", false);
        assertEquals(TestDatabase.jdbcUrl(SCHEMA), ui.textOf("urlField"));

        // The stored secret is never shown again: it has to be retyped.
        ui.assertLabelEmpty("dbPasswordField");

        // Same endpoint and user: the trusted keyring secret is reused.
        ui.clickButton("Probar conexión");
        ui.assertLabel("statusLabel", "Conexión correcta.");

        // A different endpoint does not reuse the stored secret.
        ui.type("urlField", TestDatabase.jdbcUrl("monte_erp_onboarding_other"));
        ui.clickButton("Probar conexión");
        ui.assertLabel("errorLabel", "Introduce la URL, el usuario y la contraseña.");
        ui.type("urlField", TestDatabase.jdbcUrl(SCHEMA));

        ui.type("dbPasswordField", "contraseña-incorrecta");
        ui.clickButton("Probar conexión");
        ui.assertLabelContains("errorLabel", "No se pudo conectar:");

        ui.clear("dbPasswordField");
        ui.type("dbPasswordField", TestDatabase.password());
        ui.clickButton("Probar conexión");
        ui.assertLabel("statusLabel", "Conexión correcta.");

        ui.clickButton("Guardar y continuar");
        ui.assertTitle("Monte ERP · Iniciar sesión");
        assertEquals(TestDatabase.username(), credentials.stored("db.user"));
        assertEquals(TestDatabase.password(), credentials.stored("db.password"));
    }

    @Test
    @DisplayName("Cancelar en configuración (sin onboarding) vuelve al login")
    void cancelReturnsToLogin() {
        Path properties = TempFiles.propertiesFile("cancel.properties",
                TestDatabase.jdbcUrl(SCHEMA));
        openSetup(false, properties, new FakeCredentialStore()
                .with("db.user", TestDatabase.username())
                .with("db.password", TestDatabase.password()));

        ui.clickButton("Cancelar");

        ui.assertTitle("Monte ERP · Iniciar sesión");
    }

    private void openSetup(boolean onboarding, Path properties, FakeCredentialStore credentials) {
        stage = FxRunner.get(Stage::new);
        app = UiTestSupport.forOnboarding(stage, properties, credentials);
        app.startAtSetup(onboarding);
        ui = new UiActions(stage);
        ui.activateWindow();
    }
}
