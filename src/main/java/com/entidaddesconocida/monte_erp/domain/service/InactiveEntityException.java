package com.entidaddesconocida.monte_erp.domain.service;

/** Signals that an entity selected in a form was deactivated before its write committed. */
public final class InactiveEntityException extends RuntimeException {

    public InactiveEntityException(String message) {
        super(message);
    }
}
