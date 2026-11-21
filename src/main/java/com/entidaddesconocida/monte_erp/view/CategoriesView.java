package com.entidaddesconocida.monte_erp.view;

import com.entidaddesconocida.monte_erp.app.AppShell;
import com.entidaddesconocida.monte_erp.domain.model.Category;
import com.entidaddesconocida.monte_erp.viewmodel.CategoriesViewModel;
import com.entidaddesconocida.monte_erp.viewmodel.CrudViewModel;

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
 * Code-behind of the category management FXML (RF-04).
 *
 * <p>Binds the table, the search and the form to the
 * {@link CategoriesViewModel} and delegates navigation to the {@link AppShell}.
 * The CRUD skeleton (selection synchronization, messages, toggle, ValidatorFX
 * decoration and the destructive confirmation) comes from {@link CrudView}; only
 * the category specific controls live here.</p>
 */
public class CategoriesView extends CrudView<Category> implements FxmlView<CategoriesViewModel> {

    @FXML
    private TableView<Category> categoriesTable;

    @FXML
    private TableColumn<Category, String> nameColumn;

    @FXML
    private TableColumn<Category, String> descriptionColumn;

    @FXML
    private TableColumn<Category, String> statusColumn;

    @FXML
    private TextField searchField;

    @FXML
    private CheckBox showInactiveCheck;

    @FXML
    private TextField nameField;

    @FXML
    private TextField descriptionField;

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
    private CategoriesViewModel viewModel;

    public CategoriesView(AppShell appShell) {
        super(appShell);
    }

    @Override
    protected CrudViewModel<Category> viewModel() {
        return viewModel;
    }

    @FXML
    private void initialize() {
        categoriesTable.setItems(viewModel.categories());
        initCommon();
    }

    @Override
    protected void setupFields() {
        nameColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getName()));
        descriptionColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().getDescription()));
        statusColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().getStatusLabel()));

        searchField.textProperty().bindBidirectional(viewModel.searchTextProperty());
        showInactiveCheck.selectedProperty().bindBidirectional(viewModel.showInactiveProperty());

        nameField.textProperty().bindBidirectional(viewModel.nameProperty());
        descriptionField.textProperty().bindBidirectional(viewModel.descriptionProperty());

        countLabel.textProperty().bind(viewModel.categoryCountProperty());
    }

    @Override
    protected void decorateFields() {
        decorate(nameField, "name", viewModel.nameErrorProperty());
        decorate(descriptionField, "description", viewModel.descriptionErrorProperty());
    }

    @Override
    protected TableView<Category> table() {
        return categoriesTable;
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
        return "Dar de baja la categoría";
    }

    @Override
    protected String deactivationContent(Category category) {
        return "¿Seguro que quieres dar de baja la categoría '" + category.getName()
                + "'? No se elimina de la base de datos ni modifica sus productos, y podrás reactivarla.";
    }

    @FXML
    private void onSave() {
        viewModel.save();
    }

    @FXML
    private void onNewCategory() {
        viewModel.newCategory();
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
