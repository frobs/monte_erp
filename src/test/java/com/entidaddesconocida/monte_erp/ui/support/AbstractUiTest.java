package com.entidaddesconocida.monte_erp.ui.support;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;

import javafx.stage.Stage;

/**
 * Base class for the TestFX tests that run against the seeded MySQL container.
 *
 * <p>Lifecycle:</p>
 * <ol>
 *   <li>{@code @BeforeAll}: start the container, migrate, seed and start the
 *       JavaFX toolkit (once per JVM).</li>
 *   <li>{@code @BeforeEach}: restore the seeded baseline and open a fresh
 *       window on the login screen.</li>
 *   <li>{@code @AfterEach}: close the window (the toolkit stays alive).</li>
 * </ol>
 */
@Tag("ui")
public abstract class AbstractUiTest {

    protected Stage stage;
    protected UiTestSupport app;
    protected UiActions ui;

    @BeforeAll
    static void prepareDatabaseAndToolkit() {
        TestDatabase.provision();
        FxRunner.ensureToolkitStarted();
    }

    @BeforeEach
    void openApplication() {
        TestDatabase.resetToSeed();
        stage = FxRunner.get(Stage::new);
        app = UiTestSupport.forSeedDatabase(stage);
        app.startAtLogin();
        ui = new UiActions(stage);
        ui.activateWindow();
    }

    protected void loginAsAdmin() {
        signIn(SeedFacts.ADMIN_USERNAME, SeedFacts.ADMIN_PASSWORD);
    }

    protected void loginAsEmployee() {
        signIn(SeedFacts.EMPLOYEE_USERNAME, SeedFacts.EMPLOYEE_PASSWORD);
    }

    protected void signIn(String username, String password) {
        ui.type("usernameField", username);
        ui.type("passwordField", password);
        ui.click("loginButton");
        ui.assertLabel("usernameLabel", username);
    }

    @AfterEach
    void closeApplication() {
        if (stage != null) {
            FxRunner.run(stage::close);
        }
    }
}
