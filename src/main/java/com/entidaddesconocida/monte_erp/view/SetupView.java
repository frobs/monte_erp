package com.entidaddesconocida.monte_erp.view;

import com.entidaddesconocida.monte_erp.app.AppShell;
import com.entidaddesconocida.monte_erp.viewmodel.SetupViewModel;

import de.saxsys.mvvmfx.FxmlView;
import de.saxsys.mvvmfx.InjectViewModel;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

/**
 * Code-behind of the database configuration FXML.
 *
 * <p>Bindings are set up in {@link #initialize()} so the ViewModel drives the
 * state and the View only renders it. Navigation is delegated to the
 * {@link AppShell}.</p>
 */
public class SetupView implements FxmlView<SetupViewModel> {

    private final AppShell appShell;

    @FXML
    private TextField urlField;

    @FXML
    private TextField dbUserField;

    @FXML
    private PasswordField dbPasswordField;

    @FXML
    private VBox adminSection;

    @FXML
    private TextField adminNameField;

    @FXML
    private TextField adminUsernameField;

    @FXML
    private PasswordField adminPasswordField;

    @FXML
    private Label errorLabel;

    @FXML
    private Label statusLabel;

    @InjectViewModel
    private SetupViewModel viewModel;

    public SetupView(AppShell appShell) {
        this.appShell = appShell;
    }

    @FXML
    private void initialize() {
        urlField.textProperty().bindBidirectional(viewModel.urlProperty());
        dbUserField.textProperty().bindBidirectional(viewModel.dbUserProperty());
        dbPasswordField.textProperty().bindBidirectional(viewModel.dbPasswordProperty());
        adminNameField.textProperty().bindBidirectional(viewModel.adminNameProperty());
        adminUsernameField.textProperty().bindBidirectional(viewModel.adminUsernameProperty());
        adminPasswordField.textProperty().bindBidirectional(viewModel.adminPasswordProperty());

        adminSection.visibleProperty().bind(viewModel.adminSectionVisibleProperty());
        adminSection.managedProperty().bind(viewModel.adminSectionVisibleProperty());

        errorLabel.textProperty().bind(viewModel.errorMessageProperty());
        errorLabel.visibleProperty().bind(viewModel.errorMessageProperty().isNotEmpty());
        errorLabel.managedProperty().bind(viewModel.errorMessageProperty().isNotEmpty());

        statusLabel.textProperty().bind(viewModel.statusMessageProperty());
        statusLabel.visibleProperty().bind(viewModel.statusMessageProperty().isNotEmpty());
        statusLabel.managedProperty().bind(viewModel.statusMessageProperty().isNotEmpty());

        viewModel.subscribe(SetupViewModel.SETUP_SUCCEEDED,
                (key, payload) -> appShell.showLogin());

        viewModel.clearMessages();
    }

    @FXML
    private void onTestConnection() {
        viewModel.testConnection();
    }

    @FXML
    private void onSave() {
        viewModel.save();
    }

    @FXML
    private void onCancel() {
        appShell.cancelSetup();
    }
}
