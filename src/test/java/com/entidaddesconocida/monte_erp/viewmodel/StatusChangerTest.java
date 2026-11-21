package com.entidaddesconocida.monte_erp.viewmodel;

import com.entidaddesconocida.monte_erp.app.BackgroundTasks;
import com.entidaddesconocida.monte_erp.domain.model.StatusWorkflow;
import com.entidaddesconocida.monte_erp.domain.model.WorkflowStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link StatusChanger}, the RF-08/RF-14 pipeline shared by the
 * order and purchase screens: domain validation, optimistic guard, conflict
 * reload and the confirmation of a committed transition.
 */
class StatusChangerTest {

    private static final String NONE_SELECTED = "Selecciona un documento.";
    private static final String INVALID = "No puede pasar de \"%s\" a \"%s\".";
    private static final String CONFLICT = "El documento ha cambiado.";

    /** Statuses of the fake pipeline: {@code PENDING -> DONE/CANCELLED}. */
    private enum FakeStatus implements WorkflowStatus {
        PENDING("Pendiente"),
        DONE("Hecho"),
        CANCELLED("Cancelado");

        private final String label;

        FakeStatus(String label) {
            this.label = label;
        }

        @Override
        public String label() {
            return label;
        }

        List<FakeStatus> transitions() {
            return this == PENDING ? List.of(DONE, CANCELLED) : List.of();
        }
    }

    /** Minimal document following the contract both entities implement. */
    private static final class Document implements StatusWorkflow<FakeStatus> {
        private final int id;
        private FakeStatus status = FakeStatus.PENDING;

        Document(int id) {
            this.id = id;
        }

        @Override
        public int getId() {
            return id;
        }

        @Override
        public FakeStatus getStatus() {
            return status;
        }

        @Override
        public List<FakeStatus> allowedTransitions() {
            return status.transitions();
        }

        @Override
        public boolean canTransitionTo(FakeStatus target) {
            return target != null && allowedTransitions().contains(target);
        }

        @Override
        public void changeStatus(FakeStatus target) {
            if (!canTransitionTo(target)) {
                throw new IllegalStateException("Transición no permitida");
            }
            status = target;
        }
    }

    private Feedback feedback;
    private WriteGuard guard;
    private AtomicInteger writes;
    private AtomicInteger reloads;
    private AtomicReference<Throwable> failure;
    private AtomicInteger selected;
    private AtomicInteger rowsUpdated;
    private boolean reloadSucceeds;
    private Document document;
    private StatusChanger<Document, FakeStatus> changer;

    @BeforeEach
    void setUp() {
        feedback = new Feedback();
        guard = new WriteGuard();
        writes = new AtomicInteger();
        reloads = new AtomicInteger();
        selected = new AtomicInteger();
        rowsUpdated = new AtomicInteger(1);
        reloadSucceeds = true;
        failure = new AtomicReference<>();
        document = new Document(42);

        StatusChanger.Screen<Document> screen = new StatusChanger.Screen<>() {
            @Override
            public boolean reloadHistory() {
                reloads.incrementAndGet();
                return reloadSucceeds;
            }

            @Override
            public Document findById(int id) {
                return id == document.getId() ? document : null;
            }

            @Override
            public void select(Document reloaded) {
                selected.incrementAndGet();
                assertSame(document, reloaded);
            }
        };
        StatusChanger.Messages<FakeStatus> messages = new StatusChanger.Messages<>(
                NONE_SELECTED, INVALID, CONFLICT,
                (id, status) -> "Documento #" + id + " en " + status.label() + ".");
        changer = new StatusChanger<>(BackgroundTasks.synchronous(), guard, feedback,
                (id, current, target) -> {
                    writes.incrementAndGet();
                    return rowsUpdated.get();
                }, screen, failure::set, messages);
    }

    @Test
    @DisplayName("No selected document: the screen message is shown and nothing is written")
    void missingDocumentShowsTheMessage() {
        changer.change(null, FakeStatus.DONE);

        assertEquals(NONE_SELECTED, feedback.error());
        assertEquals(0, writes.get());
    }

    @Test
    @DisplayName("A transition rejected by the domain never reaches the repository")
    void invalidTransitionIsRejected() {
        document.status = FakeStatus.DONE;

        changer.change(document, FakeStatus.PENDING);

        assertEquals("No puede pasar de \"Hecho\" a \"Pendiente\".", feedback.error());
        assertEquals(0, writes.get());
        assertEquals(FakeStatus.DONE, document.getStatus());
    }

    @Test
    @DisplayName("A committed transition reloads the history, reselects and confirms")
    void committedTransitionReloadsAndReselects() {
        changer.change(document, FakeStatus.DONE);

        assertEquals(1, writes.get());
        assertEquals(FakeStatus.DONE, document.getStatus());
        assertEquals(1, reloads.get());
        assertEquals(1, selected.get());
        assertEquals("Documento #42 en Hecho.", feedback.statusMessageProperty().get());
        assertEquals("", feedback.error());
    }

    @Test
    @DisplayName("Zero rows updated means someone else moved the document")
    void zeroRowsWarnsAboutTheConflict() {
        rowsUpdated.set(0);

        changer.change(document, FakeStatus.DONE);

        assertEquals(CONFLICT, feedback.error());
        assertEquals(1, reloads.get());
        assertEquals(0, selected.get());
        assertEquals(FakeStatus.PENDING, document.getStatus());
    }

    @Test
    @DisplayName("A reload failure still confirms the committed write")
    void reloadFailureStillConfirmsTheWrite() {
        reloadSucceeds = false;

        changer.change(document, FakeStatus.DONE);

        assertEquals("Documento #42 en Hecho.", feedback.statusMessageProperty().get());
        assertEquals(0, selected.get());
        assertEquals(FakeStatus.DONE, document.getStatus());
    }

    @Test
    @DisplayName("A rejected write reaches the failure handler and releases the guard")
    void rejectedWriteReachesTheFailureHandler() {
        RuntimeException boom = new RuntimeException("boom");
        changer = new StatusChanger<>(BackgroundTasks.synchronous(), guard, feedback,
                (id, current, target) -> {
                    throw boom;
                }, new StatusChanger.Screen<>() {
                    @Override
                    public boolean reloadHistory() {
                        return true;
                    }

                    @Override
                    public Document findById(int id) {
                        return document;
                    }

                    @Override
                    public void select(Document reloaded) {
                    }
                }, failure::set, new StatusChanger.Messages<>(NONE_SELECTED, INVALID, CONFLICT,
                        (id, status) -> ""));

        changer.change(document, FakeStatus.DONE);

        assertSame(boom, failure.get());
        assertFalse(guard.isBusy());
        assertEquals(FakeStatus.PENDING, document.getStatus());
    }

    @Test
    @DisplayName("A transition is dropped while another write is in flight")
    void transitionIsDroppedWhileAWriteIsInFlight() {
        guard.tryBegin();

        changer.change(document, FakeStatus.DONE);

        assertEquals(0, writes.get());
        assertEquals(FakeStatus.PENDING, document.getStatus());
        assertEquals("", feedback.error());
    }

    @Test
    @DisplayName("The offered transitions follow the selected document")
    void offeredTransitionsFollowTheSelection() {
        changer.updateFor(document);
        assertEquals(List.of(FakeStatus.DONE, FakeStatus.CANCELLED),
                changer.availableTransitions());

        changer.updateFor(null);
        assertTrue(changer.availableTransitions().isEmpty());
    }
}
