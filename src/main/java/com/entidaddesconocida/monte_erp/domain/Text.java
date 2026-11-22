package com.entidaddesconocida.monte_erp.domain;

/**
 * Pure text helpers shared by the persistence and the screens.
 *
 * <p>It has no dependency on JavaFX, JDBC or the application, so both layers can
 * use it without inverting the architecture.</p>
 */
public final class Text {

    private Text() {
        // Utility class: never instantiated.
    }

    /**
     * Normalizes an optional text field: trims the value and turns a blank one
     * into {@code null}, so the database stores {@code NULL} instead of an empty
     * string.
     *
     * @return the trimmed value, or {@code null} when it is {@code null}, empty
     *         or whitespace only
     */
    public static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
