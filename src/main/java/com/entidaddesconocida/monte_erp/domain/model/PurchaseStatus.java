package com.entidaddesconocida.monte_erp.domain.model;

import java.util.List;

/**
 * State of a supplier purchase, matching the {@code PURCHASE.status} ENUM of the
 * database (RF-14 / CU-10).
 *
 * <p>Like {@link OrderStatus}, each state declares the states reachable from it in
 * {@link #transitions()}: {@code pending → received/cancelled} and {@code
 * received → returned}. A received purchase can never be cancelled, only returned
 * to the supplier. That rule is a property of the state, not of a validation
 * method that compares strings.</p>
 */
public enum PurchaseStatus {

    /** State of a freshly created purchase: its lots are not available yet. */
    PENDING("pending", "Pendiente"),

    /** State of a received purchase: its lots are available in the stock. */
    RECEIVED("received", "Recibida"),

    /** State of a cancelled purchase: only reachable while it is still pending. */
    CANCELLED("cancelled", "Cancelada"),

    /** State of a purchase given back to the supplier: terminal, stock removed. */
    RETURNED("returned", "Devuelta");

    private final String dbValue;
    private final String label;

    PurchaseStatus(String dbValue, String label) {
        this.dbValue = dbValue;
        this.label = label;
    }

    /** Value stored in the {@code ENUM} column. */
    public String dbValue() {
        return dbValue;
    }

    /** Human readable label, for the user interface (Spanish). */
    public String label() {
        return label;
    }

    /**
     * Transitions allowed from this state, in button order. Empty for a terminal
     * state ({@code returned}/{@code cancelled}). The list is immutable.
     */
    public List<PurchaseStatus> transitions() {
        return switch (this) {
            case PENDING -> List.of(RECEIVED, CANCELLED);
            case RECEIVED -> List.of(RETURNED);
            case CANCELLED, RETURNED -> List.of();
        };
    }

    /** Whether the state is final: no transition leaves it. */
    public boolean isTerminal() {
        return transitions().isEmpty();
    }

    /**
     * Whether the purchase no longer feeds the stock: it was returned to the
     * supplier (its lots were emptied) or cancelled before arriving (its lots
     * never became available). Its lots are kept for traceability, but they are
     * no longer stock, so the reports show them marked and leave them out of the
     * totals (RF-12).
     */
    public boolean isReturnedOrCancelled() {
        return this == RETURNED || this == CANCELLED;
    }

    /**
     * Parses the value stored in the database.
     *
     * @throws IllegalArgumentException when the value is not one of the ENUM
     *                                  values, so a corrupted row fails loudly
     */
    public static PurchaseStatus fromDb(String value) {
        for (PurchaseStatus status : values()) {
            if (status.dbValue.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Estado de compra desconocido: " + value);
    }
}
