package com.entidaddesconocida.monte_erp.viewmodel;

import com.entidaddesconocida.monte_erp.app.SessionValidator;

import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.transformation.FilteredList;

import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Optional CRUD layer for the screens that add the in-memory search and the
 * "show inactive" checkbox (RF-03 products and RF-04 categories).
 *
 * <p>Wraps {@link CrudViewModel#items} in a {@link FilteredList} whose predicate
 * is composed by the subclass (the search text and, for products, the category
 * filter), and implements {@link #refreshItems()} by alternating the active and
 * inactive queries. If that reload fails while toggling the checkbox, the
 * checkbox is rolled back so it never disagrees with the rows still on
 * screen.</p>
 *
 * <p>{@code UsersViewModel} does not extend this layer: users have no search and
 * no active/inactive visibility toggle.</p>
 *
 * @param <T> entity managed by the screen
 */
public abstract class FilterableCrudViewModel<T> extends CrudViewModel<T> {

    private static final Logger LOGGER = Logger.getLogger(FilterableCrudViewModel.class.getName());

    /** Filtered view bound to the table. */
    protected final FilteredList<T> filteredItems = new FilteredList<>(items);

    /** Free text matched by {@link #matches(Object)}. */
    protected final StringProperty searchText = new SimpleStringProperty("");

    /** {@code true} to list logically deleted rows instead of active ones. */
    protected final BooleanProperty showInactive = new SimpleBooleanProperty(false);

    /** Guards the checkbox rollback so reverting does not fire the reload listener again. */
    private boolean revertingShowInactive;

    protected FilterableCrudViewModel() {
        this(null);
    }

    protected FilterableCrudViewModel(SessionValidator sessionValidator) {
        super(sessionValidator);
        // Active and inactive rows come from different queries, so toggling the
        // checkbox reloads the master list. If the reload fails the checkbox is
        // rolled back, so it never disagrees with the rows still on screen.
        showInactive.addListener((observable, oldValue, newValue) -> {
            if (revertingShowInactive) {
                return;
            }
            if (!ensureActiveSession()) {
                revertShowInactive(oldValue);
                return;
            }
            try {
                refreshItems();
            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, "Could not reload the list", e);
                revertShowInactive(oldValue);
                fail(DATABASE_ERROR);
            }
        });
    }

    /**
     * Binds the filter predicate to the given dependencies and registers the
     * counter listeners. Called by every subclass from its own constructor,
     * because the dependencies (search text, category filter) are subclass
     * fields.
     */
    protected final void bindFilter(Observable... dependencies) {
        // Filters run in memory over the loaded list; FilteredList lives in
        // javafx.base, so the ViewModel stays testable without the toolkit.
        filteredItems.predicateProperty().bind(Bindings.createObjectBinding(
                () -> this::matches, dependencies));
        items.addListener((ListChangeListener<T>) change -> updateCount());
        filteredItems.addListener((ListChangeListener<T>) change -> updateCount());
    }

    /**
     * Reloads the master list with the active or inactive rows, depending on
     * {@link #showInactive}. A reload can drop the selected row (e.g. it moved
     * to the other list): the form must not keep editing a row that is no
     * longer shown.
     */
    @Override
    protected final void refreshItems() throws SQLException {
        List<T> loaded = showInactive.get() ? loadInactive() : loadActive();
        T selected = selectedItem.get();
        items.setAll(loaded);
        if (selected != null && items.stream().noneMatch(item -> itemId(item) == itemId(selected))) {
            selectedItem.set(null);
        }
    }

    /** {@code true} when the given row must be kept by the in-memory filters. */
    protected abstract boolean matches(T item);

    /** Active rows query. */
    protected abstract List<T> loadActive() throws SQLException;

    /** Logically deleted rows query. */
    protected abstract List<T> loadInactive() throws SQLException;

    public final StringProperty searchTextProperty() {
        return searchText;
    }

    public final BooleanProperty showInactiveProperty() {
        return showInactive;
    }

    /** Rolls the visibility checkbox back without firing the listener again. */
    private void revertShowInactive(boolean previousValue) {
        revertingShowInactive = true;
        try {
            showInactive.set(previousValue);
        } finally {
            revertingShowInactive = false;
        }
    }
}
