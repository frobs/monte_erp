package com.entidaddesconocida.monte_erp.viewmodel;

import com.entidaddesconocida.monte_erp.app.BackgroundTasks;
import com.entidaddesconocida.monte_erp.app.LatestQuery;
import com.entidaddesconocida.monte_erp.app.SessionGuard;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The list half of a CRUD screen: master list, selection, messages, toggle,
 * counter and the save/deactivate/activate templates.
 *
 * <p>Extracted from {@link CrudViewModel} (A3b) so a screen can <strong>compose</strong>
 * this object instead of inheriting a template. The screen supplies an
 * {@link Entity} with the entity-specific operations (query, write, rules, id,
 * active flag, counter) and a {@link FormModel} with the form side; this class
 * wires them and implements the templates that used to be copy-pasted:
 * {@link #save()} (validate → business rules → write → reload),
 * {@link #deactivate()} / {@link #activate()} (selection and state guards →
 * write → reload) and the asynchronous list reloads with the "newest query
 * wins" guard.</p>
 *
 * <p>{@link CrudViewModel} keeps its API for the five existing screens by
 * delegating to this collaborator, so the composition path and the inheritance
 * path run exactly the same code. A new screen can use it without a superclass:
 * a plain ViewModel holding its properties, a {@link FormModel} and a
 * {@code CrudActions}.</p>
 *
 * <p>The write runs off the JavaFX thread through {@link BackgroundTasks}; the
 * list queries do too. Selection change populates/clears the form through the
 * {@link FormModel}, so the screen does not have to wire the listener.</p>
 *
 * @param <T> entity managed by the screen
 */
public final class CrudActions<T> implements CrudScreen<T> {

    /**
     * Entity-specific operations of a CRUD screen. The screen implements them
     * (or adapts its existing methods to them); this class calls them.
     */
    public interface Entity<T> {

        /** Builds the master-list query (the criteria are captured on the UI thread). */
        Callable<List<T>> rowsQuery();

        /** Extra data loaded only by {@link #load()} (e.g. the product combos). */
        default void loadAuxiliaryData() throws SQLException {
        }

        /** Builds the blocking write from the values currently in the form. */
        Callable<Void> write() throws SQLException;

        /** Side effect after the commit, before the reload. */
        default void afterWrite() {
        }

        /** Extra business checks (duplicates, own rules) after the field validation. */
        boolean validateBusinessRules() throws SQLException;

        /** Called when the unique constraint fires while writing. */
        void handleIntegrityViolation();

        void doDeactivate(int id) throws SQLException;

        void doActivate(int id) throws SQLException;

        boolean itemIsActive(T item);

        int itemId(T item);

        /** Extra guard before deactivating (e.g. the current user cannot be disabled). */
        default boolean canDeactivate(T item) {
            return true;
        }

        /** Extra guard before activating an item. */
        default boolean canActivate(T item) {
            return true;
        }

        /** Whether the toggle must be disabled for an active selection (e.g. the current user). */
        default boolean isToggleDisabledForActive(T item) {
            return false;
        }

        /** Footer text, e.g. {@code "Productos: " + items.size()}. */
        String countText();
    }

    private static final Logger LOGGER = Logger.getLogger(CrudActions.class.getName());

    private static final String DATABASE_ERROR = ViewMessages.DATABASE_ERROR;
    private static final String RELOAD_ERROR = ViewMessages.RELOAD_ERROR;

    private final Entity<T> entity;
    private final FormModel<T> form;
    private final CrudLabels<T> labels;
    private final Feedback feedback;
    private final SessionGuard sessionGuard;
    private final BackgroundTasks backgroundTasks;

    private final ObservableList<T> items = FXCollections.observableArrayList();
    private final ObjectProperty<T> selectedItem = new SimpleObjectProperty<>();
    private final ReadOnlyStringWrapper formTitle = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper toggleLabel = new ReadOnlyStringWrapper();
    private final ReadOnlyBooleanWrapper toggleDisabled = new ReadOnlyBooleanWrapper(true);
    private final ReadOnlyStringWrapper itemCount = new ReadOnlyStringWrapper("");
    private final WriteGuard writeGuard = new WriteGuard();

    /**
     * List queries: only the newest submission is applied, so a slow query for a
     * previous search text cannot overwrite the rows of the current one.
     */
    private final LatestQuery rowsQueries;

    public CrudActions(Entity<T> entity, FormModel<T> form, CrudLabels<T> labels,
                       Feedback feedback, SessionGuard sessionGuard,
                       BackgroundTasks backgroundTasks) {
        this.entity = entity;
        this.form = form;
        this.labels = labels;
        this.feedback = feedback;
        this.sessionGuard = sessionGuard;
        this.backgroundTasks = backgroundTasks;
        this.rowsQueries = new LatestQuery(backgroundTasks);
        // Selecting a row loads it into the form; clearing the selection resets
        // the form so a stale row can never be edited/saved by accident.
        selectedItem.addListener((observable, oldValue, newValue) -> {
            if (newValue == null) {
                form.clear();
            } else {
                form.populate(newValue);
            }
            refreshToggleState();
        });
    }

    // --- API pública común ---

    /** Loads (or reloads) the list and resets the form. */
    public void load() {
        feedback.clear();
        if (!ensureActiveSession()) {
            return;
        }
        // The combos are bounded queries; the list is not, so it runs off the UI
        // thread and comes back as an already built list.
        try {
            entity.loadAuxiliaryData();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Could not load the form data", e);
            // The form is reset even on failure: otherwise the title and the
            // toggle button would stay blank on the "database is down" screen.
            // fail() goes last because clearForm() clears the message.
            resetForm();
            feedback.fail(DATABASE_ERROR);
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
     * thread. Failures are reported through the default handler; the filter layer
     * passes its own so it can roll back the control that triggered the reload.
     */
    public void refreshItems() {
        refreshItems(this::reportLoadFailure);
    }

    /** Same as {@link #refreshItems()} with a caller supplied failure handler. */
    public void refreshItems(Consumer<Throwable> onFailure) {
        submitRows(this::applyItems, onFailure);
    }

    /** Clears the form so a brand new item can be typed in. */
    public void newItem() {
        feedback.clear();
        resetForm();
    }

    /**
     * Creates the item typed in the form, or updates the selected one. The
     * template is: validate the fields → business rules (duplicates, own rules)
     * → {@link Entity#write()} → reload.
     *
     * <p>Validation and the rules stay on the UI thread (they read the form and
     * need the immediate feedback); the {@link Callable} built by
     * {@link Entity#write()} is what runs in the background, and the reload
     * happens in its success callback, back on the UI thread.</p>
     */
    public void save() {
        if (!ensureActiveSession()) {
            return;
        }
        form.startValidation();
        form.refresh();
        if (form.hasErrors()) {
            feedback.fail(form.summary());
            return;
        }
        if (writeGuard.isBusy()) {
            return;
        }

        Callable<Void> write;
        try {
            if (!entity.validateBusinessRules()) {
                return;
            }
            // Built here on purpose: write() reads the form fields and captures
            // the entity to persist; the JDBC call it returns is what blocks.
            write = entity.write();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Could not save the item", e);
            feedback.fail(DATABASE_ERROR);
            return;
        }
        writeGuard.tryBegin();
        backgroundTasks.submit(
                write,
                ignored -> {
                    writeGuard.end();
                    entity.afterWrite();
                    reloadAfterWrite(labels.saveSuccessMessage());
                },
                this::onWriteFailure);
    }

    /** Logical deletion of the selected item (never a physical delete). */
    public void deactivate() {
        if (!ensureActiveSession() || writeGuard.isBusy()) {
            return;
        }
        T selected = selectedItem.get();
        if (selected == null) {
            feedback.fail(labels.notSelectedMessage());
            return;
        }
        if (!entity.itemIsActive(selected)) {
            feedback.fail(labels.alreadyInactiveMessage());
            return;
        }
        if (!entity.canDeactivate(selected)) {
            return;
        }
        try {
            entity.doDeactivate(entity.itemId(selected));
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Could not deactivate the item", e);
            feedback.fail(DATABASE_ERROR);
            return;
        }
        reloadAfterWrite(labels.deactivatedMessage().apply(selected));
    }

    /** Brings a previously deleted item back to active. */
    public void activate() {
        if (!ensureActiveSession() || writeGuard.isBusy()) {
            return;
        }
        T selected = selectedItem.get();
        if (selected == null) {
            feedback.fail(labels.notSelectedMessage());
            return;
        }
        if (entity.itemIsActive(selected)) {
            feedback.fail(labels.alreadyActiveMessage());
            return;
        }
        if (!entity.canActivate(selected)) {
            return;
        }
        try {
            entity.doActivate(entity.itemId(selected));
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Could not activate the item", e);
            feedback.fail(DATABASE_ERROR);
            return;
        }
        reloadAfterWrite(labels.activatedMessage().apply(selected));
    }

    /** Revalidates the session, dropping the screen state when it is over. */
    public boolean ensureActiveSession() {
        return sessionGuard.requireActive(this::clearSessionState, feedback::fail);
    }

    /** Recomputes the item counter from the entity. */
    public void updateCount() {
        itemCount.set(entity.countText());
    }

    /** Registers the item counter on the master list. */
    public void watchItemsForCount() {
        items.addListener((ListChangeListener<T>) change -> updateCount());
    }

    public void setFormTitle(String title) {
        formTitle.set(title);
    }

    /** Shows {@code message} as the error summary and clears the status message. */
    public void fail(String message) {
        feedback.fail(message);
    }

    /** Shows {@code message} as the status message and clears the error summary. */
    public void succeed(String message) {
        feedback.succeed(message);
    }

    /** Drops the error summary, leaving the status message alone. */
    public void clearErrorMessage() {
        feedback.clearError();
    }

    /** Master list currently loaded (active or inactive rows, depending on the screen). */
    @Override
    public ObservableList<T> items() {
        return items;
    }

    /** Currently selected item, or {@code null} when none is selected. */
    public ObjectProperty<T> selectedItem() {
        return selectedItem;
    }

    @Override
    public ObjectProperty<T> selectedItemProperty() {
        return selectedItem;
    }

    @Override
    public boolean itemIsActive(T item) {
        return entity.itemIsActive(item);
    }

    public StringProperty errorMessageProperty() {
        return feedback.errorMessageProperty();
    }

    public StringProperty statusMessageProperty() {
        return feedback.statusMessageProperty();
    }

    /** {@code "Nuevo …"} or {@code "Editar …"}, depending on the selection. */
    public ReadOnlyStringProperty formTitleProperty() {
        return formTitle.getReadOnlyProperty();
    }

    /** Label of the activate/deactivate button, derived from the selected item. */
    public ReadOnlyStringProperty toggleLabelProperty() {
        return toggleLabel.getReadOnlyProperty();
    }

    /** {@code true} when the activate/deactivate button must be disabled. */
    public ReadOnlyBooleanProperty toggleDisabledProperty() {
        return toggleDisabled.getReadOnlyProperty();
    }

    /** Footer text with the number of items currently shown. */
    public ReadOnlyStringProperty itemCountProperty() {
        return itemCount.getReadOnlyProperty();
    }

    /** Label of the destructive toggle action; public so the View can style the button. */
    public String deactivateLabel() {
        return labels.deactivateLabel();
    }

    // --- Interno ---

    /**
     * Submits a list query and drops its outcome when another one has been
     * submitted since, so a stale result never replaces fresher rows.
     */
    private void submitRows(Consumer<List<T>> onSuccess, Consumer<Throwable> onFailure) {
        rowsQueries.submit(entity.rowsQuery(), onSuccess, onFailure);
    }

    /**
     * Default reaction to a failed list query: the form is reset (otherwise the
     * title and the toggle button stay blank on the "database is down" screen) and
     * the message goes last, because the form clear drops it.
     */
    private void reportLoadFailure(Throwable failure) {
        LOGGER.log(Level.SEVERE, "Could not load the list", failure);
        resetForm();
        feedback.fail(DATABASE_ERROR);
    }

    private void onWriteFailure(Throwable failure) {
        writeGuard.end();
        if (failure instanceof SQLIntegrityConstraintViolationException) {
            LOGGER.log(Level.WARNING, "Integrity constraint violation on save", failure);
            entity.handleIntegrityViolation();
            return;
        }
        LOGGER.log(Level.SEVERE, "Could not save the item", failure);
        feedback.fail(DATABASE_ERROR);
    }

    /** Clears the selection and the form; the listener ends up in the form clear. */
    private void resetForm() {
        selectedItem.set(null);
        form.clear();
        refreshToggleState();
    }

    /** Drops the master list and the form of a session that is over. */
    private void clearSessionState() {
        items.clear();
        resetForm();
    }

    /**
     * Recomputes the toggle label/disabled state from the selected item. Private
     * on purpose: it runs from the {@code selectedItem} listener registered in
     * the constructor, so an override would read screen fields too early.
     */
    private void refreshToggleState() {
        T selected = selectedItem.get();
        if (selected == null) {
            toggleLabel.set(deactivateLabel());
            toggleDisabled.set(true);
            return;
        }
        if (entity.itemIsActive(selected)) {
            toggleLabel.set(deactivateLabel());
            toggleDisabled.set(entity.isToggleDisabledForActive(selected));
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
                    feedback.succeed(successMessage);
                },
                failure -> {
                    LOGGER.log(Level.WARNING, "The write succeeded but the reload failed", failure);
                    resetForm();
                    feedback.fail(RELOAD_ERROR);
                });
    }

    /**
     * Replaces the rows and drops a selection that no longer belongs to the list
     * (a row can vanish after a filter change or a write). Runs on the UI thread.
     */
    private void applyItems(List<T> loaded) {
        items.setAll(loaded);
        T selected = selectedItem.get();
        if (selected != null
                && items.stream().noneMatch(item -> entity.itemId(item) == entity.itemId(selected))) {
            selectedItem.set(null);
        }
    }
}
