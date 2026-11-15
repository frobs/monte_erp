package com.entidaddesconocida.monte_erp.app;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link BackgroundTasks}.
 *
 * <p>They check the two guarantees the ViewModels rely on: the synchronous
 * implementation runs inline, and the virtual-thread implementation runs the task
 * on a virtual thread while delivering the callbacks through the supplied
 * marshaller. The JavaFX toolkit is never started.</p>
 */
class BackgroundTasksTest {

    @Test
    @DisplayName("synchronous() ejecuta la tarea y el callback en el mismo hilo")
    void synchronousRunsInline() {
        BackgroundTasks tasks = BackgroundTasks.synchronous();
        Thread caller = Thread.currentThread();
        AtomicReference<Thread> taskThread = new AtomicReference<>();
        CopyOnWriteArrayList<String> events = new CopyOnWriteArrayList<>();

        tasks.submit(
                () -> {
                    taskThread.set(Thread.currentThread());
                    return "ok";
                },
                result -> events.add("success:" + result),
                failure -> events.add("failure:" + failure.getMessage()));

        assertEquals(caller, taskThread.get());
        assertEquals(java.util.List.of("success:ok"), events);
    }

    @Test
    @DisplayName("synchronous() entrega el fallo al callback, sin propagarlo")
    void synchronousDeliversFailure() {
        BackgroundTasks tasks = BackgroundTasks.synchronous();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        tasks.submit(
                () -> {
                    throw new IllegalStateException("boom");
                },
                result -> failure.set(new AssertionError("unexpected success")),
                failure::set);

        assertInstanceOf(IllegalStateException.class, failure.get());
        assertEquals("boom", failure.get().getMessage());
    }

    @Test
    @DisplayName("virtualThreads() ejecuta la tarea en un hilo virtual y marshala el resultado")
    void virtualThreadsRunOffTheCallingThread() throws InterruptedException {
        CountDownLatch delivered = new CountDownLatch(1);
        AtomicReference<Thread> taskThread = new AtomicReference<>();
        AtomicReference<String> result = new AtomicReference<>();
        // The deliverer runs inline here: no toolkit is started.
        Consumer<Runnable> inline = Runnable::run;

        BackgroundTasks.virtualThreads(inline).submit(
                () -> {
                    taskThread.set(Thread.currentThread());
                    return "hecho";
                },
                value -> {
                    result.set(value);
                    delivered.countDown();
                },
                failure -> delivered.countDown());

        assertTrue(delivered.await(5, TimeUnit.SECONDS), "the callback was never delivered");
        assertTrue(taskThread.get().isVirtual(), "the task must run on a virtual thread");
        assertEquals("hecho", result.get());
    }
}
