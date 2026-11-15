package com.entidaddesconocida.monte_erp.app;

import javafx.application.Platform;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Runs the blocking database work of the ViewModels <strong>off the JavaFX
 * thread</strong> and delivers the outcome back on it.
 *
 * <p>Every ViewModel action used to run its JDBC queries on the UI thread, so a
 * slow query, a report over the whole history or a {@code SELECT … FOR UPDATE}
 * waiting for another session froze the window (no repaint, no cancel) until the
 * statement finished or MySQL timed out. With this seam the query runs on a
 * <strong>virtual thread</strong> — a per-task, cheap thread from
 * {@link Thread#startVirtualThread(Runnable)} — and only the state update
 * ({@code clear} the error, {@code setAll} the rows, publish the message) is
 * marshalled through {@link Platform#runLater(Runnable)}, which is what JavaFX
 * requires.</p>
 *
 * <p>Unit tests inject {@link #synchronous()}, which runs everything inline: the
 * ViewModels keep their observable, straight-line behavior and no JavaFX toolkit
 * is needed. The application injects {@link #virtualThreads()} once, in its
 * composition root.</p>
 */
public interface BackgroundTasks {

    /**
     * Runs {@code task} away from the UI thread and hands its result, or its
     * failure, to the matching callback on the UI thread.
     */
    <T> void submit(Callable<T> task, Consumer<T> onSuccess, Consumer<Throwable> onFailure);

    /**
     * Runs everything inline, on the calling thread. This is the implementation
     * used by the tests, and the default of the ViewModel constructors.
     */
    static BackgroundTasks synchronous() {
        return new BackgroundTasks() {
            @Override
            public <T> void submit(Callable<T> task, Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
                T result;
                try {
                    result = task.call();
                } catch (Exception failure) {
                    onFailure.accept(failure);
                    return;
                }
                onSuccess.accept(result);
            }
        };
    }

    /**
     * Runs each task on its own virtual thread and delivers the callbacks through
     * {@link Platform#runLater(Runnable)}. Use it only after the JavaFX toolkit
     * has started.
     */
    static BackgroundTasks virtualThreads() {
        return virtualThreads(Platform::runLater);
    }

    /**
     * Same as {@link #virtualThreads()} with an explicit callback delivery, so the
     * threading of the task can be tested without starting the JavaFX toolkit.
     *
     * @param deliver marshals a callback onto the UI thread
     */
    static BackgroundTasks virtualThreads(Consumer<Runnable> deliver) {
        return new BackgroundTasks() {
            @Override
            public <T> void submit(Callable<T> task, Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
                Thread.startVirtualThread(() -> {
                    T result;
                    try {
                        result = task.call();
                    } catch (Exception failure) {
                        deliver.accept(() -> onFailure.accept(failure));
                        return;
                    }
                    deliver.accept(() -> onSuccess.accept(result));
                });
            }
        };
    }
}
