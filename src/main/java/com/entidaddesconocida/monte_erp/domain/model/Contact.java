package com.entidaddesconocida.monte_erp.domain.model;

/**
 * Contact data shared by customers (RF-06) and suppliers (RF-05).
 *
 * <p>Both entities carry exactly the same contact columns ({@code name},
 * {@code phone}, {@code email}, {@code address}) and the same logical deletion
 * flag, so the screens and repositories can be written once against this
 * contract. The concrete entity still owns its table and its own semantics.</p>
 */
public interface Contact {

    int getId();

    String getName();

    String getPhone();

    String getEmail();

    String getAddress();

    boolean isActive();
}
