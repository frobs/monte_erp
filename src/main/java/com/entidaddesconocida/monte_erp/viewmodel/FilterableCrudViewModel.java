package com.entidaddesconocida.monte_erp.viewmodel;

import com.entidaddesconocida.monte_erp.app.BackgroundTasks;
import com.entidaddesconocida.monte_erp.app.SessionValidator;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.transformation.FilteredList;

import java.util.List;
import java.util.concurrent.Callable;
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
 * <p>The subclass builds its queries through {@link #activeQuery()} /
 * {@link #inactiveQuery()}; {@link #bindFilter} only registers the reload
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
        this(null, BackgroundTasks.synchronous());
    }

    protected FilterableCrudViewModel(SessionValidator sessionValidator) {
        this(sessionValidator, BackgroundTasks.synchronous());
    }

    protected FilterableCrudViewModel(SessionValidator sessionValidator,
                                      BackgroundTasks backgroundTasks) {
        super(sessionValidator, backgroundTasks);
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
            // The reload is asynchronous: the checkbox is rolled back only when
            // the query fails, so it never disagrees with the rows on screen.
            refreshItems(failure -> {
                LOGGER.log(Level.SEVERE, "Could not reload the list", failure);
                revertShowInactive(oldValue);
                fail(DATABASE_ERROR);
            });
        });
    }

    /**
     * Registers the counter and reloads the list whenever one of the given
     * filter properties changes. Called by every subclass from its own
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
        // Only the master list is watched: filteredItems derives from it, so a
        // listener on both would recompute the counter twice for every change.
        watchItemsForCount();
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
     * Active or inactive rows, depending on {@link #showInactive}. The flag is
     * read here, on the UI thread, and only the query runs in the background. A
     * reload can drop the selected row (e.g. it moved to the other list or no
     * longer matches the search): the base class clears the form in that case.
     */
    @Override
    protected final Callable<List<T>> rowsQuery() {
        return showInactive.get() ? inactiveQuery() : activeQuery();
    }

    private void reloadFilteredItems() {
        if (!ensureActiveSession()) {
            return;
        }
        refreshItems(failure -> {
            LOGGER.log(Level.SEVERE, "Could not reload the filtered list", failure);
            fail(DATABASE_ERROR);
        });
    }

    /**
     * Builds the active-rows query. It runs on the UI thread, so the criteria are
     * captured here; the returned {@link Callable} is executed in the background.
     */
    protected abstract Callable<List<T>> activeQuery();

    /**
     * Builds the logically deleted rows query, with the same capture rule as
     * {@link #activeQuery()}.
     */
    protected abstract Callable<List<T>> inactiveQuery();

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
