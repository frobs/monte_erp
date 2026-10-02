package com.entidaddesconocida.monte_erp;

import com.entidaddesconocida.monte_erp.app.AppContext;
import com.entidaddesconocida.monte_erp.app.AppShell;
import com.entidaddesconocida.monte_erp.app.SessionState;
import com.entidaddesconocida.monte_erp.infrastructure.config.DataSeeder;
import com.entidaddesconocida.monte_erp.infrastructure.config.DatabaseConnection;
import com.entidaddesconocida.monte_erp.infrastructure.config.MigrationRunner;

import de.saxsys.mvvmfx.MvvmFX;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Monte ERP JavaFX application.
 *
 * <p>Before showing any window it applies the pending database migrations and
 * makes sure the initial development users exist. If any of that fails the user
 * is warned and the application exits: opening a login screen without a database
 * makes no sense.</p>
 *
 * <p>The application then wires the MVVM layer: {@link SessionState} →
 * {@link AppShell} → {@link AppContext} → mvvmFX custom dependency injector,
 * and finally asks the shell to show the login view.</p>
 */
public class MonteErpApplication extends Application {

    private static final Logger LOGGER = Logger.getLogger(MonteErpApplication.class.getName());

    @Override
    public void start(Stage stage) throws Exception {
        try {
            int migrations = MigrationRunner.migrate();
            int users = DataSeeder.seed();
            LOGGER.log(Level.INFO, "Database ready: {0} migration(s) applied, {1} user(s) created",
                    new Object[]{migrations, users});
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Could not prepare the database", e);
            showFatalError(e);
            return;
        }

        SessionState session = new SessionState();
        AppShell appShell = new AppShell(session);
        AppContext context = AppContext.production(session, appShell);

        MvvmFX.setCustomDependencyInjector(context::create);
        appShell.start(stage);
    }

    @Override
    public void stop() {
        DatabaseConnection.close();
    }

    private void showFatalError(Exception e) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Monte ERP");
        alert.setHeaderText("No se pudo preparar la base de datos");
        alert.setContentText("""
                %s

                Comprueba que el contenedor MySQL esté arrancado y que la configuración
                de /application.properties sea correcta.""".formatted(e.getMessage()));
        alert.showAndWait();
        Platform.exit();
    }
}
