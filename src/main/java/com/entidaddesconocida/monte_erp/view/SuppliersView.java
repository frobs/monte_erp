package com.entidaddesconocida.monte_erp.view;

import com.entidaddesconocida.monte_erp.app.AppShell;
import com.entidaddesconocida.monte_erp.domain.model.Supplier;
import com.entidaddesconocida.monte_erp.viewmodel.CrudViewModel;
import com.entidaddesconocida.monte_erp.viewmodel.SuppliersViewModel;

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
 * Code-behind of the supplier management FXML (RF-05).
 *
 * <p>Binds the table, the search and the form to the
 * {@link SuppliersViewModel} and delegates navigation to the {@link AppShell}.
 * The CRUD skeleton (selection synchronization, messages, toggle, ValidatorFX
 * decoration and the destructive confirmation) comes from {@link CrudView}; only
 * the supplier specific controls live here.</p>
 */
public class SuppliersView extends CrudView<Supplier> implements FxmlView<SuppliersViewModel> {

    /** Same limits as the ViewModel (and the VARCHAR columns): the mask stops
     * the user from typing more than the field can store. */
    private static final int MAX_PHONE_LENGTH = 20;
    private static final int MAX_EMAIL_LENGTH = 100;

    @FXML
    private TableView<Supplier> suppliersTable;

    @FXML
    private TableColumn<Supplier, String> nameColumn;

    @FXML
    private TableColumn<Supplier, String> phoneColumn;

    @FXML
    private TableColumn<Supplier, String> emailColumn;

    @FXML
    private TableColumn<Supplier, String> addressColumn;

    @FXML
    private TableColumn<Supplier, String> statusColumn;

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
    private SuppliersViewModel viewModel;

    public SuppliersView(AppShell appShell) {
        super(appShell);
    }

    @Override
    protected CrudViewModel<Supplier> viewModel() {
        return viewModel;
    }

    @FXML
    private void initialize() {
        suppliersTable.setItems(viewModel.suppliers());
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

        countLabel.textProperty().bind(viewModel.supplierCountProperty());
    }

    @Override
    protected void decorateFields() {
        decorate(nameField, "name", viewModel.nameErrorProperty());
        decorate(phoneField, "phone", viewModel.phoneErrorProperty());
        decorate(emailField, "email", viewModel.emailErrorProperty());
        decorate(addressField, "address", viewModel.addressErrorProperty());
    }

    @Override
    protected TableView<Supplier> table() {
        return suppliersTable;
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
        return "Dar de baja el proveedor";
    }

    @Override
    protected String deactivationContent(Supplier supplier) {
        return "¿Seguro que quieres dar de baja el proveedor '" + supplier.getName()
                + "'? No se elimina de la base de datos ni modifica sus compras, y podrás reactivarlo.";
    }

    @FXML
    private void onSave() {
        viewModel.save();
    }

    @FXML
    private void onNewSupplier() {
        viewModel.newSupplier();
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
