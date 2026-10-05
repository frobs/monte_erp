package com.entidaddesconocida.monte_erp.infrastructure.config;

/**
 * Lightweight wrapper around a JDBC URL so the DI container can bind it
 * type-safely instead of using the raw {@link String} type.
 *
 * <p>Using a dedicated type prevents collisions when future components need
 * other {@code String} dependencies (API keys, file paths, etc.).</p>
 */
public record DatabaseUrl(String value) {
}
