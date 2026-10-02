package com.entidaddesconocida.monte_erp;

import com.entidaddesconocida.monte_erp.infrastructure.config.DataSeeder;
import com.entidaddesconocida.monte_erp.infrastructure.config.DatabaseConnection;
import com.entidaddesconocida.monte_erp.infrastructure.config.MigrationRunner;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
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
 */
public class MonteErpApplication extends Application {

    private static final Logger LOGGER = Logger.getLogger(MonteErpApplication.class.getName());

    private static final String LOGIN_VIEW = "/fxml/login.fxml";
    private static final double LOGIN_WIDTH = 460;
    private static final double LOGIN_HEIGHT = 480;

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

        FXMLLoader loader = new FXMLLoader(MonteErpApplication.class.getResource(LOGIN_VIEW));
        Scene scene = new Scene(loader.load(), LOGIN_WIDTH, LOGIN_HEIGHT);
        stage.setTitle("Monte ERP · Iniciar sesión");
        stage.setScene(scene);
        stage.centerOnScreen();
        stage.show();
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
