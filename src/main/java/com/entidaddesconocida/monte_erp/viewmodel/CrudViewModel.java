package com.entidaddesconocida.monte_erp.viewmodel;

import de.saxsys.mvvmfx.ViewModel;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Common skeleton of the three CRUD screens (RF-02 users, RF-03 products and
 * RF-04 categories).
 *
 * <p>Owns the state every screen repeats — the master list, the selected row,
 * the form title, the error/status messages, the activate/deactivate toggle and
 * the item counter — and implements the templates that were copy-pasted in the
 * three ViewModels: {@link #save()} (validate → business rules → write →
 * reload), {@link #deactivate()} / {@link #activate()} (selection and state
 * guards → write → reload) and {@link #reloadAfterWrite(String)}.</p>
 *
 * <p>Everything entity specific is a hook: how to load the list, how to write,
 * how to map an item to its id/active flag, which labels and messages to show
 * and how to fill/clear the form. A hook is added here only when at least two
 * of the three real screens need it; otherwise it stays in the subclass.</p>
 *
 * <p>Like the concrete ViewModels, this class is deliberately free of
 * ValidatorFX: the per-field rules are exposed as one error property per field,
 * so it stays testable with plain JUnit and no JavaFX toolkit.</p>
 *
 * @param <T> entity managed by the screen (User, Product or Category)
 */
public abstract class CrudViewModel<T> implements ViewModel {

    private static final Logger LOGGER = Logger.getLogger(CrudViewModel.class.getName());

    /** Message shown when the database cannot be reached. */
    protected static final String DATABASE_ERROR =
            "No se pudo conectar con la base de datos. Comprueba que MySQL esté arrancado.";

    /** Message shown when the write succeeded but the list could not be reloaded. */
    protected static final String RELOAD_ERROR =
            "Los cambios se guardaron, pero no se pudo recargar la lista. Pulsa 'Actualizar'.";

    /** Master list currently loaded (active or inactive rows, depending on the subclass). */
    protected final ObservableList<T> items = FXCollections.observableArrayList();

    /** Currently selected row, or {@code null} when none is selected. */
    protected final ObjectProperty<T> selectedItem = new SimpleObjectProperty<>();

    private final StringProperty errorMessage = new SimpleStringProperty("");
    private final StringProperty statusMessage = new SimpleStringProperty("");
    private final ReadOnlyStringWrapper formTitle = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper toggleLabel = new ReadOnlyStringWrapper();
    private final ReadOnlyBooleanWrapper toggleDisabled = new ReadOnlyBooleanWrapper(true);
    private final ReadOnlyStringWrapper itemCount = new ReadOnlyStringWrapper("");

    /** {@code true} only after the first save attempt, so fields are not red while typing the first time. */
    protected boolean validateOnEdit;

    /**
     * Live re-validation once the user has already tried to save. The summary
     * label is refreshed too, otherwise the combined message of the failed save
     * stays visible after the user has corrected the fields.
     */
    private final ChangeListener<Object> fieldChange = (observable, oldValue, newValue) -> {
        if (validateOnEdit) {
            refreshFieldErrors();
            errorMessage.set(hasFieldErrors() ? fieldErrorsSummary() : "");
        }
    };

    protected CrudViewModel() {
        // Selecting a row loads it into the form; clearing the selection resets
        // the form so a stale row can never be edited/saved by accident.
        selectedItem.addListener((observable, oldValue, newValue) -> {
            if (newValue == null) {
                clearForm();
            } else {
                populateForm(newValue);
            }
            refreshToggleState();
        });
    }

    /**
     * Registers the live re-validation listener on the form fields. Called by
     * every subclass from its own constructor: the fields belong to the
     * subclass, so they cannot be referenced from this constructor.
     */
    protected final void watchFields(ObservableValue<?>... fields) {
        for (ObservableValue<?> field : fields) {
            field.addListener(fieldChange);
        }
    }

    /**
     * Registers an always-on live validation for a single field: the error
     * property is recomputed on every change, even before the first save
     * attempt. Only the fields with an explicit rule (today the phone of
     * suppliers and customers) use this; the rest stay behind
     * {@code validateOnEdit} through {@link #watchFields}.
     *
     * <p>After the first save attempt {@code validateOnEdit} is already
     * {@code true} and the generic listener refreshes the whole summary, so here
     * only the field error is written. Before that moment the summary is updated
     * directly, but it is cleared only when it still holds this field's previous
     * message: a business or database error shown in the meantime must survive.</p>
     */
    protected final void watchLiveField(ObservableValue<?> field,
            Supplier<String> rule, ReadOnlyStringWrapper error) {
        field.addListener((observable, oldValue, newValue) -> {
            String previous = error.get();
            String message = rule.get();
            error.set(message);
            if (validateOnEdit) {
                return;
            }
            if (!message.isBlank()) {
                fail(message);
            } else if (errorMessage.get().equals(previous)) {
                clearErrorMessage();
            }
        });
    }

    /**
     * Registers the item counter on the master list. Called by the non
     * filterable subclasses from their own constructor; the filterable layer
     * registers its own listeners together with the filter binding.
     */
    protected final void watchItemsForCount() {
        items.addListener((ListChangeListener<T>) change -> updateCount());
    }

    // --- API pública común (los nombres actuales de cada pantalla se conservan
    // con métodos de una línea en las subclases: load, save, deactivate, …) ---

    /** Loads (or reloads) the list and resets the form. */
    public final void load() {
        errorMessage.set("");
        statusMessage.set("");
        try {
            loadAuxiliaryData();
            refreshItems();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Could not load the list", e);
            // The form is reset even on failure: otherwise the title and the
            // toggle button would stay blank on the "database is down" screen.
            // fail() goes last because clearForm() clears the message.
            resetForm();
            fail(DATABASE_ERROR);
            return;
        }
        resetForm();
    }

    /** Clears the form so a brand new item can be typed in. */
    public final void newItem() {
        errorMessage.set("");
        statusMessage.set("");
        resetForm();
    }

    /**
     * Creates the item typed in the form, or updates the selected one. The
     * template is: validate the fields → business rules (duplicates, own rules)
     * → {@link #write()} → reload.
     */
    public final void save() {
        validateOnEdit = true;
        refreshFieldErrors();
        if (hasFieldErrors()) {
            fail(fieldErrorsSummary());
            return;
        }

        try {
            if (!validateBusinessRules()) {
                return;
            }
            write();
        } catch (SQLIntegrityConstraintViolationException e) {
            // The unique constraint is the safety net if the check above races
            // with another insert.
            LOGGER.log(Level.WARNING, "Integrity constraint violation on save", e);
            handleIntegrityViolation();
            return;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Could not save the item", e);
            fail(DATABASE_ERROR);
            return;
        }
        reloadAfterWrite(saveSuccessMessage());
    }

    /** Logical deletion of the selected item (never a physical delete). */
    public final void deactivate() {
        T selected = selectedItem.get();
        if (selected == null) {
            fail(notSelectedMessage());
            return;
        }
        if (!itemIsActive(selected)) {
            fail(alreadyInactiveMessage());
            return;
        }
        if (!canDeactivate(selected)) {
            return;
        }
        try {
            doDeactivate(itemId(selected));
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Could not deactivate the item", e);
            fail(DATABASE_ERROR);
            return;
        }
        reloadAfterWrite(deactivatedMessage(selected));
    }

    /** Brings a previously deleted item back to active. */
    public final void activate() {
        T selected = selectedItem.get();
        if (selected == null) {
            fail(notSelectedMessage());
            return;
        }
        if (itemIsActive(selected)) {
            fail(alreadyActiveMessage());
            return;
        }
        try {
            doActivate(itemId(selected));
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Could not activate the item", e);
            fail(DATABASE_ERROR);
            return;
        }
        reloadAfterWrite(activatedMessage(selected));
    }

    /** Currently selected item, or {@code null} when none is selected. */
    public final ObjectProperty<T> selectedItemProperty() {
        return selectedItem;
    }

    public final StringProperty errorMessageProperty() {
        return errorMessage;
    }

    public final StringProperty statusMessageProperty() {
        return statusMessage;
    }

    /** {@code "Nuevo …"} or {@code "Editar …"}, depending on the selection. */
    public final ReadOnlyStringProperty formTitleProperty() {
        return formTitle.getReadOnlyProperty();
    }

    /** Label of the activate/deactivate button, derived from the selected item. */
    public final ReadOnlyStringProperty toggleLabelProperty() {
        return toggleLabel.getReadOnlyProperty();
    }

    /** {@code true} when the activate/deactivate button must be disabled. */
    public final ReadOnlyBooleanProperty toggleDisabledProperty() {
        return toggleDisabled.getReadOnlyProperty();
    }

    /** Footer text with the number of items currently shown. */
    public final ReadOnlyStringProperty itemCountProperty() {
        return itemCount.getReadOnlyProperty();
    }

    // --- Utilidades para las subclases ---

    protected final void setFormTitle(String title) {
        formTitle.set(title);
    }

    protected final void clearErrorMessage() {
        errorMessage.set("");
    }

    /** Shows {@code message} as the error summary and clears the status message. */
    protected final void fail(String message) {
        statusMessage.set("");
        errorMessage.set(message);
    }

    /** Shows {@code message} as the status message and clears the error summary. */
    protected final void succeed(String message) {
        errorMessage.set("");
        statusMessage.set(message);
    }

    /** Recomputes the item counter from the subclass. */
    protected final void updateCount() {
        itemCount.set(countText());
    }

    /** Clears the selection and the form; the listener ends up in {@link #clearForm()}. */
    private void resetForm() {
        selectedItem.set(null);
        clearForm();
        refreshToggleState();
    }

    /**
     * Recomputes the toggle label/disabled state from the selected item. Private
     * on purpose: it runs from the {@code selectedItem} listener registered in
     * the constructor, so an override would read subclass fields too early.
     */
    private void refreshToggleState() {
        T selected = selectedItem.get();
        if (selected == null) {
            toggleLabel.set(deactivateLabel());
            toggleDisabled.set(true);
            return;
        }
        if (itemIsActive(selected)) {
            toggleLabel.set(deactivateLabel());
            toggleDisabled.set(isToggleDisabledForActive(selected));
        } else {
            toggleLabel.set(activateLabel());
            toggleDisabled.set(false);
        }
    }

    /**
     * Reloads the list after a confirmed write. The write is already committed,
     * so a reload failure must not be reported as a failed save: it gets its own
     * message and the form is still reset.
     */
    private void reloadAfterWrite(String successMessage) {
        try {
            refreshItems();
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "The write succeeded but the reload failed", e);
            resetForm();
            fail(RELOAD_ERROR);
            return;
        }
        resetForm();
        succeed(successMessage);
    }

    /** Trims a possibly {@code null} value; {@code null} becomes the empty string. */
    protected static String trimmed(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * Joins the non-blank field messages with newlines, for the screens that show
     * every error at once (products and categories). Users show the first one
     * only, so that rule stays in {@code UsersViewModel}.
     */
    protected static String joinedErrors(String... messages) {
        return List.of(messages).stream()
                .filter(message -> !message.isBlank())
                .reduce((first, second) -> first + "\n" + second)
                .orElse("");
    }

    // --- Hooks específicos de cada entidad ---

    /** Loads {@link #items} from the repository (the Filterable layer implements it). */
    protected abstract void refreshItems() throws SQLException;

    /** Extra data loaded only by {@link #load()} (e.g. the product combos). */
    protected void loadAuxiliaryData() throws SQLException {
    }

    /** Inserts when there is no selection, updates the selected row otherwise. */
    protected abstract void write() throws SQLException;

    /** Extra business checks (duplicates, own rules) run after the field validation. */
    protected abstract boolean validateBusinessRules() throws SQLException;

    /** Called when the unique constraint fires while writing. */
    protected abstract void handleIntegrityViolation();

    protected abstract void doDeactivate(int id) throws SQLException;

    protected abstract void doActivate(int id) throws SQLException;

    /** {@code true} while the given item is active; public because the View picks the toggle action. */
    public abstract boolean itemIsActive(T item);

    protected abstract int itemId(T item);

    /** Extra guard before deactivating (e.g. the current user cannot be disabled). */
    protected boolean canDeactivate(T item) {
        return true;
    }

    /** Whether the toggle must be disabled for an active selection (e.g. the current user). */
    protected boolean isToggleDisabledForActive(T item) {
        return false;
    }

    /** Loads the entity data into the form fields; also sets the form title. */
    protected abstract void populateForm(T item);

    /** Clears the form fields and returns to the "new item" defaults. */
    protected abstract void clearForm();

    /** Recomputes the per-field error properties. */
    protected abstract void refreshFieldErrors();

    /** Clears the per-field error properties. */
    protected abstract void clearFieldErrors();

    protected abstract boolean hasFieldErrors();

    /** Summary shown in the error label (joined or first message, per screen). */
    protected abstract String fieldErrorsSummary();

    /** Footer text, e.g. {@code "Productos: " + filteredItems.size()}. */
    protected abstract String countText();

    protected abstract String newFormTitle();

    protected abstract String editFormTitle();

    /** Label of the destructive toggle action; public so the View can style the button. */
    public abstract String deactivateLabel();

    protected abstract String activateLabel();

    protected abstract String notSelectedMessage();

    protected abstract String alreadyActiveMessage();

    protected abstract String alreadyInactiveMessage();

    protected abstract String deactivatedMessage(T item);

    protected abstract String activatedMessage(T item);

    protected abstract String saveSuccessMessage();
}
