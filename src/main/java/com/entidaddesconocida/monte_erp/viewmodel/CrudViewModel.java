package com.entidaddesconocida.monte_erp.viewmodel;

import com.entidaddesconocida.monte_erp.app.BackgroundTasks;
import com.entidaddesconocida.monte_erp.app.SessionGuard;
import com.entidaddesconocida.monte_erp.app.SessionValidator;

import de.saxsys.mvvmfx.ViewModel;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Common skeleton shared by the five CRUD screens (RF-02 users, RF-03 products,
 * RF-04 categories, RF-05 suppliers and RF-06 customers).
 *
 * <p>Since A3b the skeleton is <strong>composed</strong>, not reimplemented: this
 * class keeps the screen contract (the entity hooks and the public API the Views
 * and the tests already use) and delegates the two halves to collaborators:</p>
 * <ul>
 *   <li>{@link CrudActions} — master list, selection, messages, toggle, counter,
 *       the save/deactivate/activate templates and the "newest query wins"
 *       guard.</li>
 *   <li>{@link FormModel} — loading/clearing the form and the per-field
 *       validation rules, their live re-validation and the summary.</li>
 * </ul>
 *
 * <p>The text labels travel in a {@link CrudLabels} value handed to the
 * constructor. A <strong>new screen does not need this superclass</strong>: it
 * can hold a {@link FormModel} and a {@link CrudActions} and implement
 * {@link CrudActions.Entity} directly. The inheritance remains for the five
 * existing screens, whose Views are typed against this class, and it exists only
 * as a thin adapter over the same collaborators, so both paths run the same
 * code.</p>
 *
 * <p>The save itself runs <strong>off the JavaFX thread</strong>: the subclass
 * builds the blocking write in {@link #write()} (that is where the form fields
 * are read and, for the users screen, where the PBKDF2 hash is computed) and the
 * template hands it to {@link BackgroundTasks}.</p>
 *
 * <p>Everything entity specific is a hook: how to load the list, how to write,
 * how to map an item to its id/active flag and how to fill/clear the form. A hook
 * is added here only when at least two of the five real screens need it;
 * otherwise it stays in the subclass.</p>
 *
 * <p>Like the concrete ViewModels, this class is deliberately free of
 * ValidatorFX: the per-field rules are exposed as one error property per field,
 * so it stays testable with plain JUnit and no JavaFX toolkit.</p>
 *
 * @param <T> entity managed by the screen (User, Product, Category, Supplier or Customer)
 */
public abstract class CrudViewModel<T> implements ViewModel, CrudScreen<T> {

    private static final Logger LOGGER = Logger.getLogger(CrudViewModel.class.getName());

    /** Message shown when the database cannot be reached. */
    protected static final String DATABASE_ERROR = ViewMessages.DATABASE_ERROR;

    protected static final String SESSION_EXPIRED = ViewMessages.SESSION_EXPIRED;

    private final CrudLabels<T> labels;
    private final SessionValidator sessionValidator;
    private final FormModel<T> form;
    private final CrudActions<T> actions;

    /** Master list currently loaded (active or inactive rows, depending on the subclass). */
    protected final ObservableList<T> items;

    /** Currently selected row, or {@code null} when none is selected. */
    protected final ObjectProperty<T> selectedItem;
    protected CrudViewModel(CrudLabels<T> labels, SessionValidator sessionValidator) {
        this(labels, sessionValidator, BackgroundTasks.synchronous());
    }

    /**
     * @param labels          labels and messages of the screen (button, selection
     *                        guards and success texts)
     * @param backgroundTasks executor of the list query and of the write; {@link
     *                        BackgroundTasks#synchronous()} in tests and a
     *                        virtual-thread executor in the application
     */
    protected CrudViewModel(CrudLabels<T> labels, SessionValidator sessionValidator,
                            BackgroundTasks backgroundTasks) {
        this.labels = labels;
        this.sessionValidator = sessionValidator;
        Feedback feedback = new Feedback();
        this.form = new FormModel<>(feedback, this::populateForm, this::clearForm);
        SessionGuard sessionGuard = new SessionGuard(LOGGER, sessionValidator,
                DATABASE_ERROR, SESSION_EXPIRED);
        this.actions = new CrudActions<>(entityHooks(), form, labels, feedback,
                sessionGuard, backgroundTasks);
        this.items = actions.items();
        this.selectedItem = actions.selectedItem();
    }

    /**
     * Adapts the protected abstract hooks of this class to the public contract of
     * {@link CrudActions.Entity}, so the subclasses keep their overrides.
     */
    private CrudActions.Entity<T> entityHooks() {
        return new CrudActions.Entity<T>() {
            @Override
            public Callable<List<T>> rowsQuery() {
                return CrudViewModel.this.rowsQuery();
            }

            @Override
            public void loadAuxiliaryData() throws SQLException {
                CrudViewModel.this.loadAuxiliaryData();
            }

            @Override
            public Callable<Void> write() throws SQLException {
                return CrudViewModel.this.write();
            }

            @Override
            public void afterWrite() {
                CrudViewModel.this.afterWrite();
            }

            @Override
            public boolean validateBusinessRules() throws SQLException {
                return CrudViewModel.this.validateBusinessRules();
            }

            @Override
            public void handleIntegrityViolation() {
                CrudViewModel.this.handleIntegrityViolation();
            }

            @Override
            public void doDeactivate(int id) throws SQLException {
                CrudViewModel.this.doDeactivate(id);
            }

            @Override
            public void doActivate(int id) throws SQLException {
                CrudViewModel.this.doActivate(id);
            }

            @Override
            public boolean itemIsActive(T item) {
                return CrudViewModel.this.itemIsActive(item);
            }

            @Override
            public int itemId(T item) {
                return CrudViewModel.this.itemId(item);
            }

            @Override
            public boolean canDeactivate(T item) {
                return CrudViewModel.this.canDeactivate(item);
            }

            @Override
            public boolean canActivate(T item) {
                return CrudViewModel.this.canActivate(item);
            }

            @Override
            public boolean isToggleDisabledForActive(T item) {
                return CrudViewModel.this.isToggleDisabledForActive(item);
            }

            @Override
            public String countText() {
                return CrudViewModel.this.countText();
            }
        };
    }

    /**
     * The form half, so a subclass registers its fields (rules, live sources)
     * from its own constructor: the properties live there.
     */
    protected final FormModel<T> form() {
        return form;
    }

    /**
     * Registers an always-on live validation for a single field: the error
     * property is recomputed on every change, even before the first save
     * attempt. Only the fields with an explicit rule (today the phone of
     * suppliers and customers) use this; the rest register their rule through
     * {@link #form()} and are revalidated after the first save attempt.
     */
    protected final void watchLiveField(ObservableValue<?> field,
            Supplier<String> rule, ReadOnlyStringWrapper error) {
        form.watchLive(field, rule, error);
    }

    /**
     * Registers the item counter on the master list. Called by the non
     * filterable subclasses from their own constructor; the filterable layer
     * registers its own listeners together with the filter binding.
     */
    protected final void watchItemsForCount() {
        actions.watchItemsForCount();
    }

    // --- API pública común (los nombres actuales de cada pantalla se conservan
    // con métodos de una línea en las subclases: load, save, deactivate, …) ---

    /** Loads (or reloads) the list and resets the form. */
    public final void load() {
        actions.load();
    }

    /**
     * Runs the master-list query in the background and applies the rows on the UI
     * thread. Failures are reported through the default handler; the filter layer
     * passes its own handler so it can roll back the control that triggered the
     * reload.
     */
    protected final void refreshItems() {
        actions.refreshItems();
    }

    /** Same as {@link #refreshItems()} with a caller supplied failure handler. */
    protected final void refreshItems(Consumer<Throwable> onFailure) {
        actions.refreshItems(onFailure);
    }

    /** Clears the form so a brand new item can be typed in. */
    public final void newItem() {
        actions.newItem();
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
        actions.save();
    }

    /** Logical deletion of the selected item (never a physical delete). */
    public final void deactivate() {
        actions.deactivate();
    }

    /** Brings a previously deleted item back to active. */
    public final void activate() {
        actions.activate();
    }

    /** Currently selected item, or {@code null} when none is selected. */
    @Override
    public final ObjectProperty<T> selectedItemProperty() {
        return selectedItem;
    }

    /** Master list bound to the table. */
    @Override
    public final ObservableList<T> items() {
        return items;
    }

    public final StringProperty errorMessageProperty() {
        return actions.errorMessageProperty();
    }

    public final StringProperty statusMessageProperty() {
        return actions.statusMessageProperty();
    }

    /** {@code "Nuevo …"} or {@code "Editar …"}, depending on the selection. */
    public final ReadOnlyStringProperty formTitleProperty() {
        return actions.formTitleProperty();
    }

    /** Label of the activate/deactivate button, derived from the selected item. */
    public final ReadOnlyStringProperty toggleLabelProperty() {
        return actions.toggleLabelProperty();
    }

    /** {@code true} when the activate/deactivate button must be disabled. */
    public final ReadOnlyBooleanProperty toggleDisabledProperty() {
        return actions.toggleDisabledProperty();
    }

    /** Footer text with the number of items currently shown. */
    public final ReadOnlyStringProperty itemCountProperty() {
        return actions.itemCountProperty();
    }

    // --- Utilidades para las subclases ---

    protected final void setFormTitle(String title) {
        actions.setFormTitle(title);
    }

    protected final void clearErrorMessage() {
        actions.clearErrorMessage();
    }

    /** Shows {@code message} as the error summary and clears the status message. */
    protected final void fail(String message) {
        actions.fail(message);
    }

    /** Shows {@code message} as the status message and clears the error summary. */
    protected final void succeed(String message) {
        actions.succeed(message);
    }

    /** Recomputes the item counter from the subclass. */
    protected final void updateCount() {
        actions.updateCount();
    }

    /** Revalidates the session, dropping the state when it is over. */
    protected final boolean ensureActiveSession() {
        return actions.ensureActiveSession();
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

    /** Trims a possibly {@code null} value; {@code null} becomes the empty string. */
    protected static String trimmed(String value) {
        return value == null ? "" : value.trim();
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

    /** Footer text, e.g. {@code "Productos: " + items.size()}. */
    protected abstract String countText();

    protected abstract String newFormTitle();

    protected abstract String editFormTitle();

    /** Label of the destructive toggle action; public so the View can style the button. */
    public final String deactivateLabel() {
        return labels.deactivateLabel();
    }
}
