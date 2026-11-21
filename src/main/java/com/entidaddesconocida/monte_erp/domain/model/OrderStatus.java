package com.entidaddesconocida.monte_erp.domain.model;

import java.util.List;

/**
 * State of a sales order, matching the {@code SALES_ORDER.status} ENUM of the
 * database (RF-07 / RF-08, decision D5).
 *
 * <p>The pipeline is a domain invariant: each state declares the states reachable
 * from it in {@link #transitions()}, in button order, and a terminal state
 * declares none. That makes an impossible transition (a retrogression, leaving a
 * terminal state, repeating the current one) <strong>unrepresentable</strong>
 * instead of something to validate against a string at run time, and it keeps the
 * database value ({@link #dbValue()}) and the label shown to the user
 * ({@link #label()}) in the same place.</p>
 */
public enum OrderStatus implements WorkflowStatus {

    /** State of a freshly created order. */
    PENDING("pending", "Pendiente"),

    /** State of an order already shipped. */
    SHIPPED("shipped", "Enviado"),

    /** State of an order delivered to the customer: terminal. */
    DELIVERED("delivered", "Entregado"),

    /** State of a cancelled order: terminal, its stock was given back (RF-08). */
    CANCELLED("cancelled", "Cancelado");

    private final String dbValue;
    private final String label;

    OrderStatus(String dbValue, String label) {
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
     * Transitions allowed from this state, in button order: {@code pending →
     * shipped/cancelled} and {@code shipped → delivered/cancelled}. Empty for a
     * terminal state. The list is immutable, so the rule cannot be mutated from
     * outside.
     */
    public List<OrderStatus> transitions() {
        return switch (this) {
            case PENDING -> List.of(SHIPPED, CANCELLED);
            case SHIPPED -> List.of(DELIVERED, CANCELLED);
            case DELIVERED, CANCELLED -> List.of();
        };
    }

    /** Whether the state is final: no transition leaves it. */
    public boolean isTerminal() {
        return transitions().isEmpty();
    }

    /**
     * Parses the value stored in the database.
     *
     * @throws IllegalArgumentException when the value is not one of the ENUM
     *                                  values, so a corrupted row fails loudly
     *                                  instead of being rendered as an unknown state
     */
    public static OrderStatus fromDb(String value) {
        for (OrderStatus status : values()) {
            if (status.dbValue.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Estado de pedido desconocido: " + value);
    }
}
