package com.entidaddesconocida.monte_erp.viewmodel;

import com.entidaddesconocida.monte_erp.app.BackgroundTasks;
import com.entidaddesconocida.monte_erp.domain.model.StatusWorkflow;
import com.entidaddesconocida.monte_erp.domain.model.WorkflowStatus;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * Status pipeline of the document selected in a master-detail screen (RF-08 for
 * sales orders, RF-14 for purchases).
 *
 * <p>Owns the transitions reachable from the selection, the domain validation
 * and the write with the optimistic guard: a 0-row update means someone else
 * moved the document in the meantime, so the history is reloaded and the user is
 * warned instead of overwriting it. The transition runs off the JavaFX thread
 * (a cancellation gives stock back inside a transaction), guarded by a
 * {@link WriteGuard} shared with the save of the same screen.</p>
 *
 * <p>Extracted from the master-detail ViewModels (A2), where both screens
 * repeated the same six steps with a different noun.</p>
 *
 * @param <T> document type ({@code SalesOrder} or {@code Purchase})
 * @param <S> status type of the document
 */
public final class StatusChanger<T extends StatusWorkflow<S>, S extends WorkflowStatus> {

    /**
     * Write of one transition under the optimistic guard.
     *
     * <p>Declares {@link Exception} because a purchase return can be rejected
     * with the checked {@code PurchaseStockConsumedException} and the sales
     * order write with a {@code java.sql.SQLException}; both reach the failure
     * callback.</p>
     *
     * @return rows updated; {@code 0} means the stored status is no longer the
     *         one the user saw, so another session moved the document
     */
    @FunctionalInterface
    public interface TransitionWriter<S> {
        int apply(int documentId, S current, S target) throws Exception;
    }

    /** Read-back the screen provides once a transition is committed. */
    public interface Screen<T> {

        /**
         * Re-runs the history query and drops the selection.
         *
         * @return {@code false} when the history could not be refreshed; the
         *         error message is already set
         */
        boolean reloadHistory();

        /** Document with the given id in the reloaded history, or {@code null}. */
        T findById(int id);

        /** Selects the reloaded document, which loads its detail again. */
        void select(T document);
    }

    /**
     * Texts of the screen: the only difference between orders and purchases is
     * the noun and the gender of the confirmation.
     */
    public record Messages<S>(String noneSelected, String invalidTransition, String conflict,
            BiFunction<Integer, S, String> confirmation) {
    }

    private final ObservableList<S> availableTransitions = FXCollections.observableArrayList();
    private final BackgroundTasks backgroundTasks;
    private final WriteGuard writeGuard;
    private final Feedback feedback;
    private final TransitionWriter<S> writer;
    private final Screen<T> screen;
    private final Consumer<Throwable> onFailure;
    private final Messages<S> messages;

    /**
     * @param onFailure reaction to a rejected write (it logs it and shows
     *                  either the domain message or the connection error)
     */
    public StatusChanger(BackgroundTasks backgroundTasks, WriteGuard writeGuard,
            Feedback feedback, TransitionWriter<S> writer, Screen<T> screen,
            Consumer<Throwable> onFailure, Messages<S> messages) {
        this.backgroundTasks = backgroundTasks;
        this.writeGuard = writeGuard;
        this.feedback = feedback;
        this.writer = writer;
        this.screen = screen;
        this.onFailure = onFailure;
        this.messages = messages;
    }

    /** Statuses reachable from the selected document, in button order. */
    public ObservableList<S> availableTransitions() {
        return availableTransitions;
    }

    /** Recomputes the offered transitions from the selected document ({@code null} = none). */
    public void updateFor(T document) {
        availableTransitions.setAll(document == null ? List.of() : document.allowedTransitions());
    }

    /**
     * Moves {@code document} to {@code target}: validates the transition in the
     * domain, persists it with the optimistic guard and reports the outcome.
     */
    public void change(T document, S target) {
        if (document == null) {
            feedback.fail(messages.noneSelected());
            return;
        }
        if (!document.canTransitionTo(target)) {
            feedback.fail(String.format(messages.invalidTransition(),
                    document.getStatus().label(), target.label()));
            return;
        }
        if (!writeGuard.tryBegin()) {
            return;
        }
        int documentId = document.getId();
        S current = document.getStatus();
        // A cancellation gives stock back inside a transaction: off the UI thread.
        backgroundTasks.submit(
                () -> writer.apply(documentId, current, target),
                updated -> {
                    writeGuard.end();
                    onCommitted(document, target, updated);
                },
                failure -> {
                    writeGuard.end();
                    onFailure.accept(failure);
                });
    }

    /** Applies a transition already committed by the repository. */
    private void onCommitted(T document, S target, int updated) {
        if (updated == 0) {
            // Someone else moved the document: reload instead of overwriting it.
            screen.reloadHistory();
            feedback.fail(messages.conflict());
            return;
        }
        document.changeStatus(target);
        String message = messages.confirmation().apply(document.getId(), target);
        if (!screen.reloadHistory()) {
            // The write is committed: confirm it even though the filter or the
            // database hides the reloaded history.
            feedback.status(message);
            return;
        }
        T reloaded = screen.findById(document.getId());
        screen.select(reloaded);
        feedback.succeed(message);
    }
}
