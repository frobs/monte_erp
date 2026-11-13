package com.entidaddesconocida.monte_erp.ui.support;

import javafx.application.Platform;
import org.testfx.util.WaitForAsyncUtils;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * JavaFX test toolkit bootstrap and small helpers to run code on the FX thread
 * from the JUnit thread.
 *
 * <p>Unlike TestFX's {@code ApplicationExtension}, the toolkit is started once
 * per JVM and never exited, so every test class can create its own {@link
 * javafx.stage.Stage} without killing the platform for the next one.</p>
 */
public final class FxRunner {

    private static final long FX_TIMEOUT_SECONDS = 30;

    private static boolean started;

    private FxRunner() {
    }

    public static synchronized void ensureToolkitStarted() {
        if (started) {
            return;
        }
        try {
            CountDownLatch ready = new CountDownLatch(1);
            Platform.startup(ready::countDown);
            if (!ready.await(FX_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("The JavaFX toolkit did not start in time");
            }
        } catch (IllegalStateException alreadyStarted) {
            // Another test class (or a previous run in the same JVM) did it first.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while starting the JavaFX toolkit", e);
        }
        Platform.setImplicitExit(false);
        started = true;
    }

    /** Runs {@code action} on the FX thread and waits for its result. */
    public static <T> T get(Callable<T> action) {
        try {
            return WaitForAsyncUtils.asyncFx(action).get(FX_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw rethrow(e.getCause());
        } catch (TimeoutException e) {
            throw new IllegalStateException("Timed out waiting for the FX thread", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the FX thread", e);
        }
    }

    public static void run(Runnable action) {
        get(() -> {
            action.run();
            return null;
        });
    }

    /** Waits (on the JUnit thread) until {@code condition} holds, polling the FX thread. */
    public static void waitUntil(String description, Callable<Boolean> condition) {
        try {
            WaitForAsyncUtils.waitFor(FX_TIMEOUT_SECONDS, TimeUnit.SECONDS, condition);
        } catch (TimeoutException e) {
            throw new AssertionError("Timed out waiting for " + description, e);
        }
    }

    public static void waitForFxEvents() {
        WaitForAsyncUtils.waitForFxEvents();
    }

    private static RuntimeException rethrow(Throwable cause) {
        if (cause instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        if (cause instanceof Error error) {
            throw error;
        }
        return new IllegalStateException(cause);
    }
}
