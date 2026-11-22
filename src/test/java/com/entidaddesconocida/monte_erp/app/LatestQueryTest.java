package com.entidaddesconocida.monte_erp.app;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link LatestQuery}.
 *
 * <p>They fix the "newest query wins" contract the ViewModels rely on once the
 * queries run off the JavaFX thread: a result or a failure of a superseded
 * submission is dropped, and {@link LatestQuery#invalidate()} discards the one
 * in flight without blocking the next. The answers are resolved by hand, so the
 * out-of-order arrival a slow query would produce is reproduced exactly.</p>
 */
class LatestQueryTest {

    private final Deque<Runnable> pending = new ArrayDeque<>();

    /** Holds every submission until the test resolves it, in any order. */
    private final BackgroundTasks deferred = new BackgroundTasks() {
        @Override
        public <T> void submit(Callable<T> task, Consumer<T> onSuccess,
                               Consumer<Throwable> onFailure) {
            pending.add(() -> {
                try {
                    onSuccess.accept(task.call());
                } catch (Exception failure) {
                    onFailure.accept(failure);
                }
            });
        }
    };

    @Test
    @DisplayName("Solo se aplica el resultado de la última consulta")
    void onlyTheNewestResultIsApplied() {
        LatestQuery queries = new LatestQuery(deferred);
        List<String> applied = new CopyOnWriteArrayList<>();

        queries.submit(() -> "vieja", applied::add, failure -> applied.add("fallo"));
        queries.submit(() -> "nueva", applied::add, failure -> applied.add("fallo"));

        // The first query resolves last: the newest result must win.
        pending.removeLast().run();
        pending.removeFirst().run();

        assertEquals(List.of("nueva"), applied);
    }

    @Test
    @DisplayName("El fallo de una consulta antigua no se reporta")
    void staleFailureIsDropped() {
        LatestQuery queries = new LatestQuery(deferred);
        List<String> applied = new CopyOnWriteArrayList<>();

        queries.<String>submit(
                () -> {
                    throw new IllegalStateException("boom");
                },
                applied::add,
                failure -> applied.add("fallo"));
        queries.submit(() -> "nueva", applied::add, failure -> applied.add("fallo"));

        pending.removeLast().run();
        pending.removeFirst().run();

        assertEquals(List.of("nueva"), applied);
    }

    @Test
    @DisplayName("invalidate() descarta la consulta en vuelo y no bloquea la siguiente")
    void invalidateDropsTheQueryInFlight() {
        LatestQuery queries = new LatestQuery(deferred);
        List<String> applied = new CopyOnWriteArrayList<>();

        queries.submit(() -> "vieja", applied::add, failure -> applied.add("fallo"));
        queries.invalidate();
        pending.removeFirst().run();

        assertEquals(List.of(), applied);

        queries.submit(() -> "nueva", applied::add, failure -> applied.add("fallo"));
        pending.removeFirst().run();

        assertEquals(List.of("nueva"), applied);
    }
}
