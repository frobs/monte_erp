package com.entidaddesconocida.monte_erp.viewmodel;

import com.entidaddesconocida.monte_erp.app.SessionValidator;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.collections.transformation.FilteredList;

import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Optional CRUD layer for the screens that add the free-text search and the
 * "show inactive" checkbox (RF-03 products, RF-04 categories, RF-05 suppliers
 * and RF-06 customers).
 *
 * <p>Every filter travels to the database: changing the search text or a combo
 * reloads the master list with the criteria in the {@code WHERE}, so no row is
 * discarded in memory. {@link #filteredItems} is still the list bound to the
 * table (and the counter), but it no longer carries a {@code Predicate}. If a
 * reload fails while toggling the checkbox, the checkbox is rolled back so it
 * never disagrees with the rows still on screen.</p>
 *
 * <p>The subclass exposes its criteria through {@link #loadActive()} /
 * {@link #loadInactive()}; {@link #bindFilter} only registers the reload
 * listeners. {@code UsersViewModel} does not extend this layer: users have no
 * search and no active/inactive visibility toggle.</p>
 *
 * @param <T> entity managed by the screen
 */
public abstract class FilterableCrudViewModel<T> extends CrudViewModel<T> {

    private static final Logger LOGGER = Logger.getLogger(FilterableCrudViewModel.class.getName());

    /** List bound to the table; the database already applied the filters. */
    protected final FilteredList<T> filteredItems = new FilteredList<>(items);

    /** Free text sent to the repository query. */
    protected final StringProperty searchText = new SimpleStringProperty("");

    /** {@code true} to list logically deleted rows instead of active ones. */
    protected final BooleanProperty showInactive = new SimpleBooleanProperty(false);

    /** Guards the checkbox rollback so reverting does not fire the reload listener again. */
    private boolean revertingShowInactive;

    /** Avoids one query per property while a batch filter change is applied. */
    private boolean suppressFilterReload;

    protected FilterableCrudViewModel() {
        this(null);
    }

    protected FilterableCrudViewModel(SessionValidator sessionValidator) {
        super(sessionValidator);
        // Active and inactive rows come from different queries, so toggling the
        // checkbox reloads the master list. If the reload fails the checkbox is
        // rolled back, so it never disagrees with the rows still on screen.
        showInactive.addListener((observable, oldValue, newValue) -> {
            if (revertingShowInactive || suppressFilterReload) {
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
     * Registers the counter listeners and reloads the list whenever one of the
     * given filter properties changes. Called by every subclass from its own
     * constructor, because the dependencies (search text, category filter, …)
     * are subclass fields.
     */
    protected final void bindFilter(ObservableValue<?>... dependencies) {
        for (ObservableValue<?> dependency : dependencies) {
            dependency.addListener((observable, oldValue, newValue) -> {
                if (!suppressFilterReload) {
                    reloadFilteredItems();
                }
            });
        }
        items.addListener((ListChangeListener<T>) change -> updateCount());
        filteredItems.addListener((ListChangeListener<T>) change -> updateCount());
    }

    /**
     * Applies a batch of filter changes without issuing one query per property.
     * The caller must invoke {@link #reloadFilters()} afterwards (or rely on the
     * {@code showInactive} change, which is not suppressed by every caller).
     */
    protected final void withoutFilterReload(Runnable changes) {
        boolean previous = suppressFilterReload;
        suppressFilterReload = true;
        try {
            changes.run();
        } finally {
            suppressFilterReload = previous;
        }
    }

    /** Reloads the list with the current filter criteria. */
    protected final void reloadFilters() {
        reloadFilteredItems();
    }

    /**
     * Reloads the master list with the active or inactive rows, depending on
     * {@link #showInactive}. A reload can drop the selected row (e.g. it moved
     * to the other list or no longer matches the search): the form must not keep
     * editing a row that is no longer shown.
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

    private void reloadFilteredItems() {
        if (!ensureActiveSession()) {
            return;
        }
        try {
            refreshItems();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Could not reload the filtered list", e);
            fail(DATABASE_ERROR);
        }
    }

    /** Active rows query, with the current filter criteria in the SQL. */
    protected abstract List<T> loadActive() throws SQLException;

    /** Logically deleted rows query, with the current filter criteria in the SQL. */
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
