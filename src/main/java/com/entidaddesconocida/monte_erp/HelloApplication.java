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
 * Aplicación JavaFX de Monte ERP.
 *
 * <p>Al arrancar, antes de mostrar ninguna pantalla: aplica las migraciones de
 * base de datos pendientes y asegura los usuarios iniciales de desarrollo. Si
 * algo de esto falla, se avisa al usuario y la aplicación termina (no tiene
 * sentido abrir un login sin base de datos).</p>
 */
public class HelloApplication extends Application {

    private static final Logger LOGGER = Logger.getLogger(HelloApplication.class.getName());

    private static final String VISTA_LOGIN = "/fxml/login.fxml";
    private static final double ANCHO_LOGIN = 460;
    private static final double ALTO_LOGIN = 480;

    @Override
    public void start(Stage stage) throws Exception {
        try {
            int migraciones = MigrationRunner.migrate();
            int usuarios = DataSeeder.seed();
            LOGGER.log(Level.INFO, "Base de datos lista: {0} migración(es) aplicada(s), {1} usuario(s) creado(s)",
                    new Object[]{migraciones, usuarios});
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "No se pudo preparar la base de datos", e);
            mostrarErrorFatal(e);
            return;
        }

        FXMLLoader loader = new FXMLLoader(HelloApplication.class.getResource(VISTA_LOGIN));
        Scene scene = new Scene(loader.load(), ANCHO_LOGIN, ALTO_LOGIN);
        stage.setTitle("Monte ERP · Iniciar sesión");
        stage.setScene(scene);
        stage.centerOnScreen();
        stage.show();
    }

    @Override
    public void stop() {
        DatabaseConnection.close();
    }

    private void mostrarErrorFatal(Exception e) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Monte ERP");
        alert.setHeaderText("No se pudo preparar la base de datos");
        alert.setContentText("""
                %s

                Revisa que el contenedor MySQL esté arrancado y que la configuración
                de /application.properties sea correcta.""".formatted(e.getMessage()));
        alert.showAndWait();
        Platform.exit();
    }
}
