package com.entidaddesconocida.monte_erp.view;

import com.entidaddesconocida.monte_erp.app.AppShell;
import com.entidaddesconocida.monte_erp.domain.model.User;
import com.entidaddesconocida.monte_erp.viewmodel.LoginViewModel;

import de.saxsys.mvvmfx.FxmlView;
import de.saxsys.mvvmfx.InjectViewModel;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

/**
 * Code-behind of the login FXML.
 *
 * <p>Bindings are set up in {@link #initialize()} so the ViewModel drives the
 * state and the View only renders it. Navigation actions are delegated to the
 * {@link AppShell}.</p>
 */
public class LoginView implements FxmlView<LoginViewModel> {

    private final AppShell appShell;

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Label errorLabel;

    @InjectViewModel
    private LoginViewModel viewModel;

    public LoginView(AppShell appShell) {
        this.appShell = appShell;
    }

    @FXML
    private void initialize() {
        usernameField.textProperty().bindBidirectional(viewModel.usernameProperty());
        passwordField.textProperty().bindBidirectional(viewModel.passwordProperty());

        errorLabel.textProperty().bind(viewModel.errorMessageProperty());
        errorLabel.visibleProperty().bind(viewModel.errorMessageProperty().isNotEmpty());
        errorLabel.managedProperty().bind(viewModel.errorMessageProperty().isNotEmpty());

        viewModel.subscribe(LoginViewModel.LOGIN_SUCCEEDED,
                (key, payload) -> appShell.showMain((User) payload[0]));

        viewModel.clearError();
    }

    @FXML
    private void onLogin() {
        viewModel.login();
        if (!viewModel.errorMessageProperty().isEmpty().get() && !viewModel.usernameProperty().isEmpty().get()) {
            passwordField.requestFocus();
        }
    }

    @FXML
    private void onChangeDatabaseConfig() {
        appShell.showSetup(false);
    }

    @FXML
    private void onExit() {
        appShell.exit();
    }
}
