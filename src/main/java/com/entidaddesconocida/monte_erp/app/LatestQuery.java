package com.entidaddesconocida.monte_erp.app;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Guard of the asynchronous queries that a newer one can supersede: only the
 * outcome of the last submission is applied.
 *
 * <p>With the queries running off the JavaFX thread (A1) a slow answer can
 * arrive after a newer one — the rows of an abandoned search text, the stock of
 * a product that is no longer selected, the lines of a previous order — and
 * paint stale data over the current screen. Each {@link #submit} takes the next
 * sequence number; its callbacks compare it with the current one and, when a
 * newer submission exists, drop the result or the failure without touching the
 * state.</p>
 *
 * <p>{@link #invalidate()} annuls the query in flight without submitting a new
 * one, for the screens that simply clear the selection (no product, no order,
 * session lost) and must still prevent the pending answer from being applied.
 * The next {@link #submit} starts a valid generation again, so an
 * {@code invalidate()} never blocks the following query.</p>
 *
 * <p>Extracted (A5) from the four copies of the counter: the master list and
 * the filtered reload of {@link com.entidaddesconocida.monte_erp.viewmodel.CrudActions},
 * the stock levels of {@code StockViewModel}, the available stock of
 * {@code SalesOrderDraft} and the order detail of {@code SalesOrderDetail}.
 * The counter is not typed on purpose: a screen may submit queries that return
 * different types (the full level snapshot and the filtered rows of the stock
 * screen) and they must still supersede each other. Like the callbacks it
 * guards, it is meant to be used from the UI thread.</p>
 */
public final class LatestQuery {

    private final BackgroundTasks backgroundTasks;

    /** Sequence of the submissions; only the newest one is applied. */
    private long generation;

    public LatestQuery(BackgroundTasks backgroundTasks) {
        this.backgroundTasks = backgroundTasks;
    }

    /**
     * Runs {@code query} in the background and hands its result, or its failure,
     * to the matching callback only when no newer submission has been made
     * meanwhile.
     *
     * @param <T> type of the result of the query
     */
    public <T> void submit(Callable<T> query, Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
        long submission = ++generation;
        backgroundTasks.submit(
                query,
                result -> {
                    if (submission == generation) {
                        onSuccess.accept(result);
                    }
                },
                failure -> {
                    if (submission == generation) {
                        onFailure.accept(failure);
                    }
                });
    }

    /**
     * Drops the outcome of the query in flight, if any, so it is not applied when
     * it finally arrives.
     */
    public void invalidate() {
        generation++;
    }
}
