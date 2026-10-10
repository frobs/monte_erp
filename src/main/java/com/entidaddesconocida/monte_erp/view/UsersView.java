package com.entidaddesconocida.monte_erp.view;

import com.entidaddesconocida.monte_erp.app.AppShell;
import com.entidaddesconocida.monte_erp.domain.model.User;
import com.entidaddesconocida.monte_erp.viewmodel.UsersViewModel;

import de.saxsys.mvvmfx.FxmlView;
import de.saxsys.mvvmfx.InjectViewModel;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

import net.synedra.validatorfx.DefaultDecoration;
import net.synedra.validatorfx.Validator;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Code-behind of the user management FXML (RF-02).
 *
 * <p>Binds the table and the form to the {@link UsersViewModel} and delegates
 * navigation to the {@link AppShell}. The confirmation dialog for the
 * destructive action (deactivation) belongs to the View.</p>
 */
public class UsersView implements FxmlView<UsersViewModel> {

    private static final Logger LOGGER = Logger.getLogger(UsersView.class.getName());

    private final AppShell appShell;
    private final Validator validator = new Validator();

    @FXML
    private TableView<User> usersTable;

    @FXML
    private TableColumn<User, String> usernameColumn;

    @FXML
    private TableColumn<User, String> nameColumn;

    @FXML
    private TableColumn<User, String> roleColumn;

    @FXML
    private TableColumn<User, String> statusColumn;

    @FXML
    private TextField nameField;

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private ComboBox<String> roleCombo;

    @FXML
    private Label formTitleLabel;

    @FXML
    private Label errorLabel;

    @FXML
    private Label statusLabel;

    @FXML
    private Label countLabel;

    @FXML
    private Button toggleActiveButton;

    @InjectViewModel
    private UsersViewModel viewModel;

    public UsersView(AppShell appShell) {
        this.appShell = appShell;
    }

    @FXML
    private void initialize() {
        usersTable.setItems(viewModel.users());
        // The selection model of a TableView is read-only, so it is synced with
        // the ViewModel through listeners in both directions.
        usersTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> viewModel.selectedUserProperty().set(newValue));
        viewModel.selectedUserProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null) {
                usersTable.getSelectionModel().clearSelection();
            } else {
                usersTable.getSelectionModel().select(newValue);
            }
        });

        usernameColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getUsername()));
        nameColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getName()));
        roleColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getRoleLabel()));
        statusColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getStatusLabel()));

        nameField.textProperty().bindBidirectional(viewModel.nameProperty());
        usernameField.textProperty().bindBidirectional(viewModel.usernameProperty());
        passwordField.textProperty().bindBidirectional(viewModel.passwordProperty());
        passwordField.promptTextProperty().bind(viewModel.passwordPromptProperty());

        roleCombo.setItems(viewModel.roleOptions());
        roleCombo.valueProperty().bindBidirectional(viewModel.roleProperty());

        formTitleLabel.textProperty().bind(viewModel.formTitleProperty());
        toggleActiveButton.textProperty().bind(viewModel.toggleLabelProperty());
        toggleActiveButton.disableProperty().bind(viewModel.toggleDisabledProperty());
        viewModel.toggleLabelProperty().addListener(
                (observable, oldValue, newValue) -> applyToggleStyle(newValue));
        applyToggleStyle(viewModel.toggleLabelProperty().get());

        errorLabel.textProperty().bind(viewModel.errorMessageProperty());
        errorLabel.visibleProperty().bind(viewModel.errorMessageProperty().isNotEmpty());
        errorLabel.managedProperty().bind(viewModel.errorMessageProperty().isNotEmpty());

        statusLabel.textProperty().bind(viewModel.statusMessageProperty());
        statusLabel.visibleProperty().bind(viewModel.statusMessageProperty().isNotEmpty());
        statusLabel.managedProperty().bind(viewModel.statusMessageProperty().isNotEmpty());

        countLabel.textProperty().bind(viewModel.userCountProperty());

        viewModel.load();

        // ValidatorFX only paints the controls; the rules live in the ViewModel
        // as per-field error properties so they stay testable without toolkit.
        DefaultDecoration.setFactory(DefaultDecoration::createStyleClassDecoration);
        decorate(nameField, "name", viewModel.nameErrorProperty());
        decorate(usernameField, "username", viewModel.usernameErrorProperty());
        decorate(passwordField, "password", viewModel.passwordErrorProperty());
    }

    /**
     * Decorates a control from the ViewModel error property of the field: the
     * ViewModel owns the rule, ValidatorFX only paints the control.
     */
    private void decorate(Node field, String key, ObservableValue<String> error) {
        validator.createCheck()
                .dependsOn(key, error)
                .withMethod(context -> {
                    String message = context.get(key);
                    if (message != null && !message.isBlank()) {
                        context.error(message);
                    }
                })
                .decorates(field)
                .immediate();
    }

    @FXML
    private void onSave() {
        viewModel.save();
    }

    @FXML
    private void onNewUser() {
        viewModel.newUser();
        nameField.requestFocus();
    }

    @FXML
    private void onRefresh() {
        viewModel.load();
    }

    @FXML
    private void onToggleActive() {
        User selected = viewModel.selectedUserProperty().get();
        if (selected == null) {
            return;
        }
        if (selected.isActive()) {
            confirmDeactivation(selected);
        } else {
            viewModel.activate();
        }
    }

    @FXML
    private void onBack() {
        appShell.showMain();
    }

    /**
     * Keeps the destructive styling only while the button actually deactivates:
     * "Activar usuario" is a recovery action and must not look dangerous.
     */
    private void applyToggleStyle(String label) {
        toggleActiveButton.getStyleClass().removeAll("danger-button", "secondary-button");
        toggleActiveButton.getStyleClass().add(UsersViewModel.TOGGLE_DEACTIVATE_LABEL.equals(label)
                ? "danger-button"
                : "secondary-button");
    }

    private void confirmDeactivation(User user) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Monte ERP");
        alert.setHeaderText("Desactivar usuario");
        alert.setContentText("¿Seguro que quieres desactivar al usuario '" + user.getUsername()
                + "'? No se elimina de la base de datos y podrás volver a activarlo.");
        alert.showAndWait()
                .filter(button -> button == ButtonType.OK)
                .ifPresent(button -> {
                    viewModel.deactivate();
                    LOGGER.log(Level.FINE, "User deactivation confirmed: {0}", user.getUsername());
                });
    }
}
