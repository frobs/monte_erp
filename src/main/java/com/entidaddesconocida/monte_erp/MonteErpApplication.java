package com.entidaddesconocida.monte_erp;

import com.entidaddesconocida.monte_erp.app.AppContext;
import com.entidaddesconocida.monte_erp.app.AppShell;
import com.entidaddesconocida.monte_erp.app.SessionState;
import com.entidaddesconocida.monte_erp.domain.model.ConnectionSettings;
import com.entidaddesconocida.monte_erp.domain.repository.UserRepository;
import com.entidaddesconocida.monte_erp.domain.service.SetupService;
import com.entidaddesconocida.monte_erp.infrastructure.MySqlUserRepository;
import com.entidaddesconocida.monte_erp.infrastructure.config.DatabaseConfig;
import com.entidaddesconocida.monte_erp.infrastructure.config.DatabaseConnection;
import com.entidaddesconocida.monte_erp.infrastructure.config.DatabaseUrl;
import com.entidaddesconocida.monte_erp.infrastructure.config.DatabaseSetupService;
import com.entidaddesconocida.monte_erp.infrastructure.config.KeyringCredentialStore;
import com.entidaddesconocida.monte_erp.infrastructure.config.MigrationRunner;

import de.saxsys.mvvmfx.MvvmFX;

import javafx.application.Application;
import javafx.stage.Stage;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Monte ERP JavaFX application.
 *
 * <p>On startup it decides which screen to show. If the configuration (URL in a
 * properties file next to the application, credentials in the OS keyring) is
 * missing or incomplete, the onboarding screen is shown. Otherwise the pending
 * migrations are applied and the login screen opens (or onboarding again when
 * no administrator user exists yet, e.g. after the database was recreated).</p>
 */
public class MonteErpApplication extends Application {

    private static final Logger LOGGER = Logger.getLogger(MonteErpApplication.class.getName());

    @Override
    public void start(Stage stage) {
        DatabaseConfig databaseConfig = new DatabaseConfig(new KeyringCredentialStore());
        UserRepository userRepository = new MySqlUserRepository();
        SetupService setupService = new DatabaseSetupService(databaseConfig, userRepository);

        SessionState session = new SessionState();
        AppShell appShell = new AppShell(session);
        AppContext context = new AppContext(session, appShell, userRepository, databaseConfig,
                setupService, () -> new DatabaseUrl(DatabaseConnection.getUrl()));

        MvvmFX.setCustomDependencyInjector(context::create);
        appShell.setStage(stage);

        try {
            databaseConfig.ensureFileExists();
            ConnectionSettings settings = databaseConfig.load();
            if (!settings.isComplete()) {
                appShell.showSetup(true);
            } else {
                prepareDatabase(setupService, settings, appShell);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Could not read the database configuration", e);
            appShell.showSetup(true);
        }

        stage.show();
    }

    private void prepareDatabase(SetupService setupService, ConnectionSettings settings,
                                 AppShell appShell) throws Exception {
        try {
            DatabaseConnection.configure(settings.url(), settings.user(), settings.password());
            MigrationRunner.migrate();
            if (!setupService.hasAdminUser()) {
                appShell.showSetup(true);
            } else {
                appShell.showLogin();
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Could not prepare the database", e);
            // The configuration exists but is wrong or unreachable: let the user fix it.
            appShell.showSetup(false);
        }
    }

    @Override
    public void stop() {
        DatabaseConnection.close();
    }
}
