package com.entidaddesconocida.monte_erp.controller;

import com.entidaddesconocida.monte_erp.infrastructure.config.DatabaseConnection;
import com.entidaddesconocida.monte_erp.domain.model.Usuario;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controlador de la pantalla principal.
 *
 * <p>Muestra el usuario conectado y su rol, y presenta los módulos del ERP. El
 * módulo "Gestión de usuarios" (RF-02) solo es visible para el rol
 * {@code admin}. Los módulos restantes quedan fuera del alcance de RF-01 y
 * responden con un aviso de "pendiente".</p>
 */
public class MainController {

    private static final Logger LOGGER = Logger.getLogger(MainController.class.getName());

    private static final String VISTA_LOGIN = "/fxml/login.fxml";
    private static final double ANCHO_LOGIN = 460;
    private static final double ALTO_LOGIN = 480;

    @FXML
    private Label bienvenidaLabel;

    @FXML
    private Label usuarioLabel;

    @FXML
    private Label rolLabel;

    @FXML
    private Label dbLabel;

    @FXML
    private Button gestionUsuariosButton;

    private Usuario usuario;

    @FXML
    private void initialize() {
        dbLabel.setText("Base de datos: " + DatabaseConnection.getUrl());
    }

    /**
     * Recibe el usuario autenticado desde {@code LoginController} al cambiar de
     * escena y adapta la pantalla a su rol.
     */
    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
        bienvenidaLabel.setText("Bienvenido/a, " + usuario.getNombre());
        usuarioLabel.setText(usuario.getUsuario());
        rolLabel.setText(usuario.getRolEtiqueta());

        boolean admin = usuario.esAdmin();
        gestionUsuariosButton.setVisible(admin);
        gestionUsuariosButton.setManaged(admin);
    }

    @FXML
    private void onGestionUsuarios() {
        mostrarPendiente("Gestión de usuarios (RF-02)",
                "Alta, edición y desactivación de usuarios.\n\nEste módulo es exclusivo del rol administrador "
                        + "y se implementará en el siguiente requisito funcional.");
    }

    @FXML
    private void onModuloPendiente(ActionEvent event) {
        String modulo = event.getSource() instanceof Button boton ? boton.getText() : "Módulo";
        mostrarPendiente(modulo, "Este módulo todavía no está implementado.");
    }

    @FXML
    private void onCerrarSesion(ActionEvent event) throws IOException {
        FXMLLoader loader = new FXMLLoader(MainController.class.getResource(VISTA_LOGIN));
        Parent root = loader.load();
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.setTitle("Monte ERP · Iniciar sesión");
        stage.setScene(new Scene(root, ANCHO_LOGIN, ALTO_LOGIN));
        stage.centerOnScreen();
    }

    @FXML
    private void onSalir() {
        Stage stage = (Stage) usuarioLabel.getScene().getWindow();
        stage.close();
    }

    private void mostrarPendiente(String titulo, String mensaje) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Monte ERP");
        alert.setHeaderText(titulo);
        alert.setContentText(mensaje);
        alert.showAndWait();
        LOGGER.log(Level.FINE, "Módulo pendiente solicitado por {0}: {1}", new Object[]{usuario, titulo});
    }
}
