package com.entidaddesconocida.monte_erp.controller;

import com.entidaddesconocida.monte_erp.domain.model.User;
import com.entidaddesconocida.monte_erp.infrastructure.config.DatabaseConnection;

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
 * Controller of the main screen.
 *
 * <p>Shows the signed in user and its role, and presents the ERP modules. The
 * "User management" module (RF-02) is only visible for the {@code admin} role.
 * The remaining modules are out of scope for RF-01 and answer with a "pending"
 * message.</p>
 *
 * <p>Language convention: identifiers, Javadoc and log records are written in
 * English, while every text the end user can read (labels, messages, window
 * titles) is written in Spanish.</p>
 */
public class MainController {

    private static final Logger LOGGER = Logger.getLogger(MainController.class.getName());

    private static final String LOGIN_VIEW = "/fxml/login.fxml";
    private static final double LOGIN_WIDTH = 460;
    private static final double LOGIN_HEIGHT = 480;

    @FXML
    private Label welcomeLabel;

    @FXML
    private Label usernameLabel;

    @FXML
    private Label roleLabel;

    @FXML
    private Label databaseLabel;

    @FXML
    private Button manageUsersButton;

    private User user;

    @FXML
    private void initialize() {
        databaseLabel.setText("Base de datos: " + DatabaseConnection.getUrl());
    }

    /**
     * Receives the authenticated user from {@code LoginController} while
     * switching scenes and adapts the screen to its role.
     */
    public void setUser(User user) {
        this.user = user;
        welcomeLabel.setText("Bienvenido/a, " + user.getName());
        usernameLabel.setText(user.getUsername());
        roleLabel.setText(user.getRoleLabel());

        boolean admin = user.isAdmin();
        manageUsersButton.setVisible(admin);
        manageUsersButton.setManaged(admin);
    }

    @FXML
    private void onManageUsers() {
        showPending("Gestión de usuarios (RF-02)",
                "Alta, edición y desactivación de usuarios.\n\nEste módulo es exclusivo del rol "
                        + "administrador y se implementará en el siguiente requisito funcional.");
    }

    @FXML
    private void onPendingModule(ActionEvent event) {
        String module = event.getSource() instanceof Button button ? button.getText() : "Módulo";
        showPending(module, "Este módulo todavía no está implementado.");
    }

    @FXML
    private void onSignOut(ActionEvent event) throws IOException {
        FXMLLoader loader = new FXMLLoader(MainController.class.getResource(LOGIN_VIEW));
        Parent root = loader.load();
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.setTitle("Monte ERP · Iniciar sesión");
        stage.setScene(new Scene(root, LOGIN_WIDTH, LOGIN_HEIGHT));
        stage.centerOnScreen();
    }

    @FXML
    private void onExit() {
        Stage stage = (Stage) usernameLabel.getScene().getWindow();
        stage.close();
    }

    private void showPending(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Monte ERP");
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
        LOGGER.log(Level.FINE, "Pending module requested by {0}: {1}", new Object[]{user, title});
    }
}
