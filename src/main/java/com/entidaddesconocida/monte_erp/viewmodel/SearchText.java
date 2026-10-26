package com.entidaddesconocida.monte_erp.viewmodel;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Pure helper that normalizes the in-memory search terms so the application
 * searches the same way MySQL does with the {@code utf8mb4_unicode_ci}
 * collation: <strong>case-insensitive and accent-insensitive</strong>
 * (decision D5 / RF-11).
 *
 * <p>{@link #normalize(String)} lowercases with {@link Locale#ROOT}, decomposes
 * with {@link Normalizer.Form#NFD} and drops the combining marks ({@code \p{M}}),
 * so {@code "Café"} becomes {@code "cafe"}. {@link #matches(String, String...)}
 * uses it to compare the term against each field; an empty (or {@code null})
 * term means "everything matches", i.e. no filter.</p>
 *
 * <p>The class is a JDK-only utility (like {@code PasswordHasher}), with a
 * private constructor and only static methods.</p>
 */
public final class SearchText {

    private SearchText() {
        // Utility class: never instantiated.
    }

    /**
     * Lowercases, decomposes and removes the combining marks of the value, so
     * accented and unaccented spellings compare equal.
     *
     * @return the normalized text; {@code null} becomes the empty string
     */
    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    /**
     * Whether the search term is empty or any of the fields contains it, both
     * normalized (case- and accent-insensitive).
     *
     * @param search the typed term; empty or {@code null} matches everything
     * @param fields the values to compare against (name, SKU, …)
     */
    public static boolean matches(String search, String... fields) {
        String needle = normalize(search).trim();
        if (needle.isEmpty()) {
            return true;
        }
        if (fields == null) {
            return false;
        }
        for (String field : fields) {
            if (normalize(field).contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
