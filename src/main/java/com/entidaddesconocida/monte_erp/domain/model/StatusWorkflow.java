package com.entidaddesconocida.monte_erp.domain.model;

import java.util.List;

/**
 * Document whose status moves through a validated pipeline: a sales order
 * (RF-08) or a supplier purchase (RF-14).
 *
 * <p>The methods are the ones both entities already exposed; declaring them as
 * an interface lets the screens share one status changer (A2) without
 * duplicating the optimistic guard, the conflict handling and the
 * "already moved by someone else" reload of the history.</p>
 *
 * @param <S> status type of the document
 */
public interface StatusWorkflow<S extends WorkflowStatus> {

    /** Identifier of the document in its table. */
    int getId();

    /** Current status. */
    S getStatus();

    /** Statuses reachable from the current one, in button order (empty in a terminal one). */
    List<S> allowedTransitions();

    /** Whether the document can move to {@code target} from its current status. */
    boolean canTransitionTo(S target);

    /**
     * Single gate for the in-memory change: validates the transition and moves
     * the document, or throws {@link IllegalStateException}.
     */
    void changeStatus(S target);
}
