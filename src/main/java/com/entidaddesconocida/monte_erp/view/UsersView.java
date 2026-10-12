package com.entidaddesconocida.monte_erp.view;

import com.entidaddesconocida.monte_erp.app.AppShell;
import com.entidaddesconocida.monte_erp.domain.model.User;
import com.entidaddesconocida.monte_erp.viewmodel.CrudViewModel;
import com.entidaddesconocida.monte_erp.viewmodel.UsersViewModel;

import de.saxsys.mvvmfx.FxmlView;
import de.saxsys.mvvmfx.InjectViewModel;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

/**
 * Code-behind of the user management FXML (RF-02).
 *
 * <p>Binds the table and the form to the {@link UsersViewModel} and delegates
 * navigation to the {@link AppShell}. The CRUD skeleton (selection
 * synchronization, messages, toggle, ValidatorFX decoration and the destructive
 * confirmation) comes from {@link CrudView}; only the user specific controls
 * (password field and role combo) live here.</p>
 */
public class UsersView extends CrudView<User> implements FxmlView<UsersViewModel> {

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
        super(appShell);
    }

    @Override
    protected CrudViewModel<User> viewModel() {
        return viewModel;
    }

    @FXML
    private void initialize() {
        usersTable.setItems(viewModel.users());
        initCommon();
    }

    @Override
    protected void setupFields() {
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

        countLabel.textProperty().bind(viewModel.userCountProperty());
    }

    @Override
    protected void decorateFields() {
        decorate(nameField, "name", viewModel.nameErrorProperty());
        decorate(usernameField, "username", viewModel.usernameErrorProperty());
        decorate(passwordField, "password", viewModel.passwordErrorProperty());
    }

    @Override
    protected TableView<User> table() {
        return usersTable;
    }

    @Override
    protected Button toggleButton() {
        return toggleActiveButton;
    }

    @Override
    protected Label formTitleLabel() {
        return formTitleLabel;
    }

    @Override
    protected Label errorLabel() {
        return errorLabel;
    }

    @Override
    protected Label statusLabel() {
        return statusLabel;
    }

    @Override
    protected String deactivationHeader() {
        return "Desactivar usuario";
    }

    @Override
    protected String deactivationContent(User user) {
        return "¿Seguro que quieres desactivar al usuario '" + user.getUsername()
                + "'? No se elimina de la base de datos y podrás volver a activarlo.";
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
        toggleActive();
    }

    @FXML
    private void onBack() {
        back();
    }
}
