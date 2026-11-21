package com.entidaddesconocida.monte_erp.viewmodel;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.time.LocalDate;

/**
 * Criteria of a history screen: date range, counterparty combo and status
 * combo.
 *
 * <p>Every criterion travels to the database as a filter object, so each
 * deliberate change re-runs the query instead of filtering the loaded list in
 * memory, and a full reset has to issue a single query: {@link #reset()} clears
 * the four properties without notifying and the owner re-runs the history once.</p>
 *
 * <p>Extracted from the master-detail ViewModels (A2), which repeated the four
 * properties, the two option lists, the suppression flag and the four listeners.</p>
 *
 * @param <E> counterparty of the document (customer of an order, supplier of a purchase)
 * @param <S> status of the document
 */
public final class HistoryFilter<E, S> {

    private final ObjectProperty<LocalDate> fromDate = new SimpleObjectProperty<>();
    private final ObjectProperty<LocalDate> toDate = new SimpleObjectProperty<>();
    private final ObjectProperty<E> entity = new SimpleObjectProperty<>();
    private final ObjectProperty<S> status = new SimpleObjectProperty<>();
    private final ObservableList<E> entityOptions = FXCollections.observableArrayList();
    private final ObservableList<S> statusOptions;
    private final Runnable onChange;

    /** Guards a batched reset so it issues a single query. */
    private boolean suspended;

    /**
     * @param statuses every status of the domain, in the order the combo shows them
     * @param onChange action that re-runs the query, called once per deliberate
     *                 change (never during {@link #reset()})
     */
    public HistoryFilter(S[] statuses, Runnable onChange) {
        this.statusOptions = FXCollections.observableArrayList(statuses);
        this.onChange = onChange;
        // The DatePicker only fires when the user commits a value, so this is
        // one reload per deliberate change.
        fromDate.addListener((observable, oldValue, newValue) -> changed());
        toDate.addListener((observable, oldValue, newValue) -> changed());
        entity.addListener((observable, oldValue, newValue) -> changed());
        status.addListener((observable, oldValue, newValue) -> changed());
    }

    /** Lower bound; {@code null} means "unbounded". */
    public ObjectProperty<LocalDate> fromDateProperty() {
        return fromDate;
    }

    /** Upper bound; {@code null} means "unbounded". */
    public ObjectProperty<LocalDate> toDateProperty() {
        return toDate;
    }

    /** Counterparty filter; {@code null} means "every one". */
    public ObjectProperty<E> entityProperty() {
        return entity;
    }

    /** Status filter; {@code null} means "every status". */
    public ObjectProperty<S> statusProperty() {
        return status;
    }

    /**
     * Counterparties offered by the filter: every one, active or not, so an old
     * document of a counterparty given up can still be listed (RF-11).
     */
    public ObservableList<E> entityOptions() {
        return entityOptions;
    }

    /** Statuses offered by the filter, in domain order. */
    public ObservableList<S> statusOptions() {
        return statusOptions;
    }

    /**
     * Drops every criterion in one shot without issuing a query: the owner
     * re-runs the history once afterwards.
     */
    public void reset() {
        suspended = true;
        try {
            fromDate.set(null);
            toDate.set(null);
            entity.set(null);
            status.set(null);
        } finally {
            suspended = false;
        }
    }

    private void changed() {
        if (!suspended) {
            onChange.run();
        }
    }
}
