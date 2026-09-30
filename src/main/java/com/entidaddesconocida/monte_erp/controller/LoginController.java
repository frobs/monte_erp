package com.entidaddesconocida.monte_erp.controller;

import com.entidaddesconocida.monte_erp.infrastructure.UserRepository;
import com.entidaddesconocida.monte_erp.domain.model.Usuario;
import com.entidaddesconocida.monte_erp.security.PasswordHasher;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controlador de la pantalla de inicio de sesión (RF-01).
 *
 * <p>Valida las credenciales contra {@code USUARIO}, rechaza los usuarios
 * desactivados y abre la pantalla principal pasándole el usuario autenticado.
 * Sin lógica de negocio: toda la persistencia se delega en {@link UserRepository}.</p>
 */
public class LoginController {

    private static final Logger LOGGER = Logger.getLogger(LoginController.class.getName());

    private static final String VISTA_PRINCIPAL = "/fxml/main.fxml";
    private static final double ANCHO_PRINCIPAL = 1000;
    private static final double ALTO_PRINCIPAL = 660;

    @FXML
    private TextField usuarioField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Label errorLabel;

    private final UserRepository userRepository = new UserRepository();

    @FXML
    private void initialize() {
        ocultarError();
    }

    @FXML
    private void onLogin(ActionEvent event) {
        String login = usuarioField.getText() == null ? "" : usuarioField.getText().trim();
        String password = passwordField.getText() == null ? "" : passwordField.getText();

        if (login.isEmpty() || password.isEmpty()) {
            mostrarError("Introduce usuario y contraseña.");
            return;
        }

        try {
            Usuario usuario = userRepository.buscarPorUsuario(login);

            if (usuario == null || !PasswordHasher.verify(password, usuario.getPasswordHash())) {
                mostrarError("Usuario o contraseña incorrectos.");
                passwordField.clear();
                passwordField.requestFocus();
                return;
            }
            if (!usuario.isActivo()) {
                mostrarError("El usuario está desactivado. Contacta con un administrador.");
                return;
            }

            abrirPantallaPrincipal(event, usuario);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error de base de datos durante el login", e);
            mostrarError("No se pudo conectar con la base de datos. Revisa que MySQL esté arrancado.");
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "No se pudo cargar " + VISTA_PRINCIPAL, e);
            mostrarError("No se pudo abrir la pantalla principal.");
        }
    }

    @FXML
    private void onSalir() {
        Stage stage = (Stage) usuarioField.getScene().getWindow();
        stage.close();
    }

    private void abrirPantallaPrincipal(ActionEvent event, Usuario usuario) throws IOException {
        FXMLLoader loader = new FXMLLoader(LoginController.class.getResource(VISTA_PRINCIPAL));
        Parent root = loader.load();
        MainController controller = loader.getController();
        controller.setUsuario(usuario);

        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.setTitle("Monte ERP · " + usuario.getNombre() + " (" + usuario.getRolEtiqueta() + ")");
        stage.setScene(new Scene(root, ANCHO_PRINCIPAL, ALTO_PRINCIPAL));
        stage.centerOnScreen();
    }

    private void mostrarError(String mensaje) {
        errorLabel.setText(mensaje);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void ocultarError() {
        errorLabel.setText("");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }
}
