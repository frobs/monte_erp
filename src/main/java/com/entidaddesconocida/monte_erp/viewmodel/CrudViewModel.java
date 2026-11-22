package com.entidaddesconocida.monte_erp.viewmodel;

import com.entidaddesconocida.monte_erp.app.BackgroundTasks;
import com.entidaddesconocida.monte_erp.app.SessionGuard;
import com.entidaddesconocida.monte_erp.app.SessionValidator;

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
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Common skeleton shared by the five CRUD screens (RF-02 users, RF-03 products,
 * RF-04 categories, RF-05 suppliers and RF-06 customers).
 *
 * <p>Owns the state every screen repeats — the master list, the selected row,
 * the form title, the error/status messages, the activate/deactivate toggle and
 * the item counter — and implements the templates that were copy-pasted in the
 * five ViewModels: {@link #save()} (validate → business rules → write →
 * reload), {@link #deactivate()} / {@link #activate()} (selection and state
 * guards → write → reload) and {@link #reloadAfterWrite(String)}.</p>
 *
 * <p>The save itself runs <strong>off the JavaFX thread</strong>: the subclass
 * builds the blocking write in {@link #write()} (that is where the form fields
 * are read and, for the users screen, where the PBKDF2 hash is computed) and
 * the template hands it to {@link BackgroundTasks}. Only the list query was
 * asynchronous before, so saving a user froze the window for the hundreds of
 * milliseconds the hash takes.</p>
 *
 * <p>Everything entity specific is a hook: how to load the list, how to write,
 * how to map an item to its id/active flag and how to fill/clear the form. The
 * texts are not hooks: they travel in a {@link CrudLabels} value handed to the
 * constructor, so a new screen declares them once instead of implementing a
 * method per string. A hook is added here only when at least two of the five
 * real screens need it; otherwise it stays in the subclass.</p>
 *
 * <p>Like the concrete ViewModels, this class is deliberately free of
 * ValidatorFX: the per-field rules are exposed as one error property per field,
 * so it stays testable with plain JUnit and no JavaFX toolkit.</p>
 *
 * @param <T> entity managed by the screen (User, Product, Category, Supplier or Customer)
 */
public abstract class CrudViewModel<T> implements ViewModel {

    private static final Logger LOGGER = Logger.getLogger(CrudViewModel.class.getName());

    /** Message shown when the database cannot be reached. */
    protected static final String DATABASE_ERROR = ViewMessages.DATABASE_ERROR;

    /** Message shown when the write succeeded but the list could not be reloaded. */
    protected static final String RELOAD_ERROR = ViewMessages.RELOAD_ERROR;

    protected static final String SESSION_EXPIRED = ViewMessages.SESSION_EXPIRED;

    private final CrudLabels<T> labels;
    private final SessionValidator sessionValidator;
    private final SessionGuard sessionGuard;
    private final BackgroundTasks backgroundTasks;

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
     * Sequence of the list queries. Only the newest submission is applied: with
     * the asynchronous reload a slow query for a previous search text could
     * otherwise overwrite the rows of the current one.
     */
    private long queryGeneration;

    /**
     * Guards a write in flight. The write ran on the UI thread before, which made
     * a double click impossible; now that it runs in the background this flag
     * stops a second save, deactivation or activation from being submitted while
     * the first one is still running.
     */
    private boolean writeInFlight;

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

    protected CrudViewModel(CrudLabels<T> labels, SessionValidator sessionValidator) {
        this(labels, sessionValidator, BackgroundTasks.synchronous());
    }

    /**
     * @param labels          labels and messages of the screen (button, selection
     *                        guards and success texts)
     * @param backgroundTasks executor of the list query; {@link
     *                        BackgroundTasks#synchronous()} in tests and a
     *                        virtual-thread executor in the application
     */
    protected CrudViewModel(CrudLabels<T> labels, SessionValidator sessionValidator,
                            BackgroundTasks backgroundTasks) {
        this.labels = labels;
        this.sessionValidator = sessionValidator;
        this.sessionGuard = new SessionGuard(LOGGER, sessionValidator,
                DATABASE_ERROR, SESSION_EXPIRED);
        this.backgroundTasks = backgroundTasks;
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
        if (!ensureActiveSession()) {
            return;
        }
        // The combos are bounded queries; the list is not, so it runs off the UI
        // thread and comes back as an already built list.
        try {
            loadAuxiliaryData();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Could not load the form data", e);
            // The form is reset even on failure: otherwise the title and the
            // toggle button would stay blank on the "database is down" screen.
            // fail() goes last because clearForm() clears the message.
            resetForm();
            fail(DATABASE_ERROR);
            return;
        }
        submitRows(
                loaded -> {
                    applyItems(loaded);
                    resetForm();
                },
                this::reportLoadFailure);
    }

    /**
     * Runs the master-list query in the background and applies the rows on the UI
     * thread. Failures are reported through {@link #reportLoadFailure(Throwable)};
     * the filter layer passes its own handler so it can roll back the control that
     * triggered the reload.
     */
    protected final void refreshItems() {
        refreshItems(this::reportLoadFailure);
    }

    /** Same as {@link #refreshItems()} with a caller supplied failure handler. */
    protected final void refreshItems(Consumer<Throwable> onFailure) {
        submitRows(this::applyItems, onFailure);
    }

    /**
     * Submits a list query and drops its outcome when another one has been
     * submitted since, so a stale result never replaces fresher rows.
     */
    private void submitRows(Consumer<List<T>> onSuccess, Consumer<Throwable> onFailure) {
        long generation = ++queryGeneration;
        backgroundTasks.submit(
                rowsQuery(),
                loaded -> {
                    if (generation == queryGeneration) {
                        onSuccess.accept(loaded);
                    }
                },
                failure -> {
                    if (generation == queryGeneration) {
                        onFailure.accept(failure);
                    }
                });
    }

    /**
     * Default reaction to a failed list query: the form is reset (otherwise the
     * title and the toggle button stay blank on the "database is down" screen) and
     * the message goes last, because {@code clearForm()} clears it.
     */
    private void reportLoadFailure(Throwable failure) {
        LOGGER.log(Level.SEVERE, "Could not load the list", failure);
        resetForm();
        fail(DATABASE_ERROR);
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
     *
     * <p>Validation and the rules stay on the UI thread (they read the form and
     * need the immediate feedback); the {@link Callable} built by
     * {@link #write()} is what runs in the background, and the reload happens in
     * its success callback, back on the UI thread.</p>
     */
    public final void save() {
        if (!ensureActiveSession()) {
            return;
        }
        validateOnEdit = true;
        refreshFieldErrors();
        if (hasFieldErrors()) {
            fail(fieldErrorsSummary());
            return;
        }
        if (writeInFlight) {
            return;
        }

        Callable<Void> write;
        try {
            if (!validateBusinessRules()) {
                return;
            }
            // Built here on purpose: write() reads the form fields and captures
            // the entity to persist; the JDBC call it returns is what blocks.
            write = write();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Could not save the item", e);
            fail(DATABASE_ERROR);
            return;
        }
        writeInFlight = true;
        backgroundTasks.submit(
                write,
                ignored -> {
                    writeInFlight = false;
                    afterWrite();
                    reloadAfterWrite(labels.saveSuccessMessage());
                },
                this::onWriteFailure);
    }

    /**
     * Reports a rejected write. The unique constraint is the safety net if the
     * duplicate check of {@link #validateBusinessRules()} raced with another
     * insert; anything else is a connection problem.
     */
    private void onWriteFailure(Throwable failure) {
        writeInFlight = false;
        if (failure instanceof SQLIntegrityConstraintViolationException) {
            LOGGER.log(Level.WARNING, "Integrity constraint violation on save", failure);
            handleIntegrityViolation();
            return;
        }
        LOGGER.log(Level.SEVERE, "Could not save the item", failure);
        fail(DATABASE_ERROR);
    }

    /** Logical deletion of the selected item (never a physical delete). */
    public final void deactivate() {
        if (!ensureActiveSession() || writeInFlight) {
            return;
        }
        T selected = selectedItem.get();
        if (selected == null) {
            fail(labels.notSelectedMessage());
            return;
        }
        if (!itemIsActive(selected)) {
            fail(labels.alreadyInactiveMessage());
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
        reloadAfterWrite(labels.deactivatedMessage().apply(selected));
    }

    /** Brings a previously deleted item back to active. */
    public final void activate() {
        if (!ensureActiveSession() || writeInFlight) {
            return;
        }
        T selected = selectedItem.get();
        if (selected == null) {
            fail(labels.notSelectedMessage());
            return;
        }
        if (itemIsActive(selected)) {
            fail(labels.alreadyActiveMessage());
            return;
        }
        if (!canActivate(selected)) {
            return;
        }
        try {
            doActivate(itemId(selected));
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Could not activate the item", e);
            fail(DATABASE_ERROR);
            return;
        }
        reloadAfterWrite(labels.activatedMessage().apply(selected));
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
    protected final boolean ensureActiveSession() {
        return sessionGuard.requireActive(this::clearSessionState, this::fail);
    }

    /** Drops the master list and the form of a session that is over. */
    private void clearSessionState() {
        items.clear();
        resetForm();
    }

    /**
     * Validator of the signed-in session, for the subclasses whose rules depend
     * on the <em>current</em> role (users): the one the screen was built with may
     * be stale after a demotion. {@code null} in the unit tests that build the
     * ViewModel by hand.
     */
    protected final SessionValidator sessionValidator() {
        return sessionValidator;
    }

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
            toggleLabel.set(labels.activateLabel());
            toggleDisabled.set(false);
        }
    }

    /**
     * Reloads the list after a confirmed write. The write is already committed,
     * so a reload failure must not be reported as a failed save: it gets its own
     * message and the form is still reset.
     */
    private void reloadAfterWrite(String successMessage) {
        submitRows(
                loaded -> {
                    applyItems(loaded);
                    resetForm();
                    succeed(successMessage);
                },
                failure -> {
                    LOGGER.log(Level.WARNING, "The write succeeded but the reload failed", failure);
                    resetForm();
                    fail(RELOAD_ERROR);
                });
    }

    /**
     * Replaces the rows and drops a selection that no longer belongs to the list
     * (a row can vanish after a filter change or a write). Runs on the UI thread.
     */
    private void applyItems(List<T> loaded) {
        items.setAll(loaded);
        T selected = selectedItem.get();
        if (selected != null && items.stream().noneMatch(item -> itemId(item) == itemId(selected))) {
            selectedItem.set(null);
        }
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
        return ViewMessages.join(messages);
    }

    // --- Hooks específicos de cada entidad ---

    /**
     * Builds the query that loads the master list. It runs on the UI thread, so
     * the current criteria (search text, combos, active/inactive toggle) are
     * captured here and the returned {@link Callable} is what the background
     * thread executes.
     */
    protected abstract Callable<List<T>> rowsQuery();

    /** Extra data loaded only by {@link #load()} (e.g. the product combos). */
    protected void loadAuxiliaryData() throws SQLException {
    }

    /**
     * Builds the blocking write from the values currently typed in the form.
     *
     * <p>Runs on the UI thread, so the form fields can be read here and the entity
     * to persist can be captured; the returned {@link Callable} runs on a
     * background thread, so <strong>every</strong> JDBC call (and any expensive
     * computation, like the PBKDF2 hash of a new password) must be inside it.</p>
     *
     * <p>Insert when there is no selection, update the selected row otherwise.</p>
     */
    protected abstract Callable<Void> write() throws SQLException;

    /**
     * Runs on the UI thread once the write of {@link #save()} is committed and
     * before the list is reloaded. Screens with a side effect that must happen
     * after the commit (the users screen refreshes the session snapshot) override
     * it; the default does nothing.
     */
    protected void afterWrite() {
    }

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

    /** Extra guard before activating an item. */
    protected boolean canActivate(T item) {
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
    public final String deactivateLabel() {
        return labels.deactivateLabel();
    }
}
