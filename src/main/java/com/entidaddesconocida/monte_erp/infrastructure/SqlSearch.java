package com.entidaddesconocida.monte_erp.infrastructure;

/**
 * Helpers shared by the JDBC adapters to translate the free-text filters into
 * SQL {@code LIKE} patterns.
 *
 * <p>The pattern is <strong>escaped</strong> so the characters the user types
 * are compared literally: {@code %} and {@code _} must not turn into wildcards.
 * The escape character is {@code !} (instead of the usual backslash) to avoid
 * MySQL string-escape ambiguity in the SQL text.</p>
 */
final class SqlSearch {

    private SqlSearch() {
        // Utility class: never instantiated.
    }

    /**
     * Builds a {@code %text%} pattern for a {@code LIKE ? ESCAPE '!'} clause.
     *
     * @return the escaped pattern, or {@code null} when the term is blank
     */
    static String likePattern(String search) {
        if (search == null) {
            return null;
        }
        String term = search.trim();
        if (term.isEmpty()) {
            return null;
        }
        String escaped = term
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }
}
