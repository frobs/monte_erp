package com.entidaddesconocida.monte_erp.view;

import com.entidaddesconocida.monte_erp.app.AppShell;
import com.entidaddesconocida.monte_erp.viewmodel.MainViewModel;

import de.saxsys.mvvmfx.FxmlView;
import de.saxsys.mvvmfx.InjectViewModel;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Code-behind of the main FXML.
 *
 * <p>Binds controls to the {@link MainViewModel} properties and delegates
 * navigation to the {@link AppShell}.</p>
 */
public class MainView implements FxmlView<MainViewModel> {

    private static final Logger LOGGER = Logger.getLogger(MainView.class.getName());

    private final AppShell appShell;

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

    @InjectViewModel
    private MainViewModel viewModel;

    public MainView(AppShell appShell) {
        this.appShell = appShell;
    }

    @FXML
    private void initialize() {
        welcomeLabel.textProperty().bind(viewModel.welcomeProperty());
        usernameLabel.textProperty().bind(viewModel.usernameProperty());
        roleLabel.textProperty().bind(viewModel.roleLabelProperty());
        databaseLabel.textProperty().bind(viewModel.databaseProperty());

        manageUsersButton.visibleProperty().bind(viewModel.adminProperty());
        manageUsersButton.managedProperty().bind(viewModel.adminProperty());
    }

    @FXML
    private void onManageUsers() {
        appShell.showUsers();
    }

    @FXML
    private void onProducts() {
        appShell.showProducts();
    }

    @FXML
    private void onCategories() {
        appShell.showCategories();
    }

    @FXML
    private void onSuppliers() {
        appShell.showSuppliers();
    }

    @FXML
    private void onCustomers() {
        appShell.showCustomers();
    }

    @FXML
    private void onSalesOrders() {
        appShell.showSalesOrders();
    }

    @FXML
    private void onPendingModule(ActionEvent event) {
        String module = event.getSource() instanceof Button button ? button.getText() : "Módulo";
        showPending(module, "Este módulo todavía no está implementado.");
    }

    @FXML
    private void onSignOut() {
        appShell.showLogin();
    }

    @FXML
    private void onExit() {
        appShell.exit();
    }

    private void showPending(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Monte ERP");
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
        LOGGER.log(Level.FINE, "Pending module requested: {0}", title);
    }
}
