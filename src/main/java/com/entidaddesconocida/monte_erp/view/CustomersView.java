package com.entidaddesconocida.monte_erp.view;

import com.entidaddesconocida.monte_erp.app.AppShell;
import com.entidaddesconocida.monte_erp.domain.model.Customer;
import com.entidaddesconocida.monte_erp.viewmodel.CrudViewModel;
import com.entidaddesconocida.monte_erp.viewmodel.CustomersViewModel;

import de.saxsys.mvvmfx.FxmlView;
import de.saxsys.mvvmfx.InjectViewModel;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

/**
 * Code-behind of the customer management FXML (RF-06).
 *
 * <p>Binds the table, the search and the form to the
 * {@link CustomersViewModel} and delegates navigation to the {@link AppShell}.
 * The CRUD skeleton (selection synchronization, messages, toggle, ValidatorFX
 * decoration and the destructive confirmation) comes from {@link CrudView}; only
 * the customer specific controls live here.</p>
 */
public class CustomersView extends CrudView<Customer> implements FxmlView<CustomersViewModel> {

    /** Same limits as the ViewModel (and the VARCHAR columns): the mask stops
     * the user from typing more than the field can store. */
    private static final int MAX_PHONE_LENGTH = 20;
    private static final int MAX_EMAIL_LENGTH = 100;

    @FXML
    private TableView<Customer> customersTable;

    @FXML
    private TableColumn<Customer, String> nameColumn;

    @FXML
    private TableColumn<Customer, String> phoneColumn;

    @FXML
    private TableColumn<Customer, String> emailColumn;

    @FXML
    private TableColumn<Customer, String> addressColumn;

    @FXML
    private TableColumn<Customer, String> statusColumn;

    @FXML
    private TextField searchField;

    @FXML
    private CheckBox showInactiveCheck;

    @FXML
    private TextField nameField;

    @FXML
    private TextField phoneField;

    @FXML
    private TextField emailField;

    @FXML
    private TextField addressField;

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
    private CustomersViewModel viewModel;

    public CustomersView(AppShell appShell) {
        super(appShell);
    }

    @Override
    protected CrudViewModel<Customer> viewModel() {
        return viewModel;
    }

    @FXML
    private void initialize() {
        customersTable.setItems(viewModel.filteredCustomers());
        initCommon();
    }

    @Override
    protected void setupFields() {
        nameColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getName()));
        phoneColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getPhone()));
        emailColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getEmail()));
        addressColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getAddress()));
        statusColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().getStatusLabel()));

        searchField.textProperty().bindBidirectional(viewModel.searchTextProperty());
        showInactiveCheck.selectedProperty().bindBidirectional(viewModel.showInactiveProperty());

        nameField.textProperty().bindBidirectional(viewModel.nameProperty());
        phoneField.textProperty().bindBidirectional(viewModel.phoneProperty());
        // Local input mask: letters never reach the phone field.
        phoneField.setTextFormatter(InputMasks.phone(MAX_PHONE_LENGTH));
        emailField.textProperty().bindBidirectional(viewModel.emailProperty());
        // Local input mask: spaces and a second @ never reach the email field.
        emailField.setTextFormatter(InputMasks.email(MAX_EMAIL_LENGTH));
        addressField.textProperty().bindBidirectional(viewModel.addressProperty());

        countLabel.textProperty().bind(viewModel.customerCountProperty());
    }

    @Override
    protected void decorateFields() {
        decorate(nameField, "name", viewModel.nameErrorProperty());
        decorate(phoneField, "phone", viewModel.phoneErrorProperty());
        decorate(emailField, "email", viewModel.emailErrorProperty());
        decorate(addressField, "address", viewModel.addressErrorProperty());
    }

    @Override
    protected TableView<Customer> table() {
        return customersTable;
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
        return "Dar de baja el cliente";
    }

    @Override
    protected String deactivationContent(Customer customer) {
        return "¿Seguro que quieres dar de baja el cliente '" + customer.getName()
                + "'? No se elimina de la base de datos ni modifica sus pedidos, y podrás reactivarlo.";
    }

    @FXML
    private void onSave() {
        viewModel.save();
    }

    @FXML
    private void onNewCustomer() {
        viewModel.newCustomer();
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
