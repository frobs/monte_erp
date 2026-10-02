package com.entidaddesconocida.monte_erp.controller;

import com.entidaddesconocida.monte_erp.domain.model.User;
import com.entidaddesconocida.monte_erp.infrastructure.UserRepository;
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
 * Controller of the sign-in screen (RF-01).
 *
 * <p>Validates the credentials against the {@code USER} table, rejects the
 * deactivated users and opens the main screen handing the authenticated user
 * over to it. No business logic: all persistence is delegated to
 * {@link UserRepository}.</p>
 *
 * <p>Language convention: identifiers, Javadoc and log records are written in
 * English, while every text the end user can read (labels, messages, window
 * titles) is written in Spanish.</p>
 */
public class LoginController {

    private static final Logger LOGGER = Logger.getLogger(LoginController.class.getName());

    private static final String MAIN_VIEW = "/fxml/main.fxml";
    private static final double MAIN_WIDTH = 1000;
    private static final double MAIN_HEIGHT = 660;

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Label errorLabel;

    private final UserRepository userRepository = new UserRepository();

    @FXML
    private void initialize() {
        hideError();
    }

    @FXML
    private void onLogin(ActionEvent event) {
        String username = usernameField.getText() == null ? "" : usernameField.getText().trim();
        String password = passwordField.getText() == null ? "" : passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            showError("Introduce usuario y contraseña.");
            return;
        }

        try {
            User user = userRepository.findByUsername(username);

            // Same message for an unknown user and a wrong password: whether the
            // login name exists is never revealed.
            if (user == null || !PasswordHasher.verify(password, user.getPasswordHash())) {
                showError("Usuario o contraseña incorrectos.");
                passwordField.clear();
                passwordField.requestFocus();
                return;
            }
            if (!user.isActive()) {
                showError("El usuario está desactivado. Contacta con un administrador.");
                return;
            }

            openMainView(event, user);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error during sign in", e);
            showError("No se pudo conectar con la base de datos. Comprueba que MySQL esté arrancado.");
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Could not load " + MAIN_VIEW, e);
            showError("No se pudo abrir la ventana principal.");
        }
    }

    @FXML
    private void onExit() {
        Stage stage = (Stage) usernameField.getScene().getWindow();
        stage.close();
    }

    private void openMainView(ActionEvent event, User user) throws IOException {
        FXMLLoader loader = new FXMLLoader(LoginController.class.getResource(MAIN_VIEW));
        Parent root = loader.load();
        MainController controller = loader.getController();
        controller.setUser(user);

        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.setTitle("Monte ERP · " + user.getName() + " (" + user.getRoleLabel() + ")");
        stage.setScene(new Scene(root, MAIN_WIDTH, MAIN_HEIGHT));
        stage.centerOnScreen();
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void hideError() {
        errorLabel.setText("");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }
}
