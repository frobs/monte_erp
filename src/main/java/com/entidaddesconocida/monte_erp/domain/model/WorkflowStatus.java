package com.entidaddesconocida.monte_erp.domain.model;

/**
 * A state of a document pipeline whose only public face is the label shown to
 * the user ({@link OrderStatus}, {@link PurchaseStatus}).
 *
 * <p>The state machine itself — which states are reachable from each one — stays
 * in the enum ({@code transitions()}); this interface is what the shared status
 * changer of the screens needs to build a message without knowing the concrete
 * enum (A2).</p>
 */
public interface WorkflowStatus {

    /** Human readable label, for the user interface (Spanish). */
    String label();
}
