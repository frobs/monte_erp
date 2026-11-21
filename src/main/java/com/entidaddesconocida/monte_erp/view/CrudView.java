package com.entidaddesconocida.monte_erp.view;

import com.entidaddesconocida.monte_erp.app.AppShell;
import com.entidaddesconocida.monte_erp.viewmodel.CrudViewModel;

import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;

import net.synedra.validatorfx.DefaultDecoration;
import net.synedra.validatorfx.Validator;

/**
 * Common code-behind shared by the five CRUD screens (RF-02 users, RF-03
 * products, RF-04 categories, RF-05 suppliers and RF-06 customers).
 *
 * <p>Centralizes what the five {@code *View} classes repeated: the two-way
 * synchronization between the {@code TableView} selection and the ViewModel, the
 * {@code errorLabel}/{@code statusLabel} bindings, the {@code formTitle} label,
 * the activate/deactivate button bindings and its destructive styling, the
 * ValidatorFX decoration and the destructive confirmation {@link Alert}.</p>
 *
 * <p>Unlike the ViewModels, this class does <strong>not</strong> implement
 * {@code FxmlView}: the concrete view declares {@code FxmlView<ConcreteViewModel>}
 * so mvvmFX keeps resolving the concrete ViewModel type from the generic
 * argument. The concrete view owns its {@code @InjectViewModel} field and exposes
 * it through {@link #viewModel()}, and its own {@code initialize()} calls
 * {@link #initialize()} so the FXML controller stays the concrete class.</p>
 *
 * <p>ValidatorFX lives in the View and only in the View: it needs the JavaFX
 * toolkit ({@code DefaultDecoration} loads an image in its static initializer),
 * so the rules stay in the ViewModel as plain error properties and this class
 * merely decorates the controls with them.</p>
 *
 * @param <T> entity managed by the screen
 */
public abstract class CrudView<T> {

    protected final AppShell appShell;
    protected final Validator validator = new Validator();

    protected CrudView(AppShell appShell) {
        this.appShell = appShell;
    }

    /** The concrete ViewModel, injected in the subclass with {@code @InjectViewModel}. */
    protected abstract CrudViewModel<T> viewModel();

    /** Table bound to the master list (the filters travel to the query). */
    protected abstract TableView<T> table();

    /** Activate/deactivate button. */
    protected abstract Button toggleButton();

    protected abstract Label formTitleLabel();

    protected abstract Label errorLabel();

    protected abstract Label statusLabel();

    protected abstract String deactivationHeader();

    protected abstract String deactivationContent(T item);

    /** Concrete bidirectional bindings and cell value factories. */
    protected abstract void setupFields();

    /** Decorates the form controls with the ViewModel per-field error properties. */
    protected abstract void decorateFields();

    /** Extra work after the first load (e.g. the product combo options). */
    protected void afterLoad() {
    }

    /**
     * Binds the common controls. Called from the concrete {@code initialize()}
     * so the inherited method is never looked up by reflection.
     */
    protected final void initCommon() {
        DefaultDecoration.setFactory(DefaultDecoration::createStyleClassDecoration);
        bindSelection(table());
        bindFormTitle(formTitleLabel());
        bindMessages(errorLabel(), statusLabel());
        bindToggle(toggleButton());
        setupFields();
        viewModel().load();
        afterLoad();
        decorateFields();
    }

    /** Loads the current selection into the form, and the form selection back into the table. */
    protected final void bindSelection(TableView<T> table) {
        // The selection model of a TableView is read-only, so it is synced with
        // the ViewModel through listeners in both directions.
        table.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> viewModel().selectedItemProperty().set(newValue));
        viewModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null) {
                table.getSelectionModel().clearSelection();
            } else {
                table.getSelectionModel().select(newValue);
            }
        });
    }

    protected final void bindFormTitle(Label label) {
        label.textProperty().bind(viewModel().formTitleProperty());
    }

    protected final void bindMessages(Label error, Label status) {
        error.textProperty().bind(viewModel().errorMessageProperty());
        error.visibleProperty().bind(viewModel().errorMessageProperty().isNotEmpty());
        error.managedProperty().bind(viewModel().errorMessageProperty().isNotEmpty());

        status.textProperty().bind(viewModel().statusMessageProperty());
        status.visibleProperty().bind(viewModel().statusMessageProperty().isNotEmpty());
        status.managedProperty().bind(viewModel().statusMessageProperty().isNotEmpty());
    }

    protected final void bindToggle(Button button) {
        button.textProperty().bind(viewModel().toggleLabelProperty());
        button.disableProperty().bind(viewModel().toggleDisabledProperty());
        viewModel().toggleLabelProperty().addListener(
                (observable, oldValue, newValue) -> applyToggleStyle(button, newValue));
        applyToggleStyle(button, viewModel().toggleLabelProperty().get());
    }

    /**
     * Decorates a control from the ViewModel error property of the field: the
     * ViewModel owns the rule, ValidatorFX only paints the control.
     */
    protected final void decorate(Node field, String key, ObservableValue<String> error) {
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

    /** Runs the toggle action: confirm the deactivation, or activate directly. */
    protected final void toggleActive() {
        T selected = viewModel().selectedItemProperty().get();
        if (selected == null) {
            return;
        }
        if (viewModel().itemIsActive(selected)) {
            confirmDeactivation(selected);
        } else {
            viewModel().activate();
        }
    }

    /** Navigation shared by the three screens. */
    protected final void back() {
        appShell.showMain();
    }

    /**
     * Keeps the destructive styling only while the button actually deactivates:
     * the recovery action must not look dangerous.
     */
    private void applyToggleStyle(Button button, String label) {
        button.getStyleClass().removeAll("danger-button", "secondary-button");
        button.getStyleClass().add(viewModel().deactivateLabel().equals(label)
                ? "danger-button"
                : "secondary-button");
    }

    private void confirmDeactivation(T item) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Monte ERP");
        alert.setHeaderText(deactivationHeader());
        alert.setContentText(deactivationContent(item));
        alert.showAndWait()
                .filter(button -> button == ButtonType.OK)
                .ifPresent(button -> viewModel().deactivate());
    }
}
