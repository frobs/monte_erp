package com.entidaddesconocida.monte_erp.viewmodel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link SearchText} (RF-11 / decision D5): the shared
 * normalization that makes the in-memory search case- and accent-insensitive,
 * like the {@code utf8mb4_unicode_ci} collation of MySQL.
 */
class SearchTextTest {

    @Test
    @DisplayName("normalize pasa a minúsculas y quita los acentos")
    void normalizeLowercasesAndStripsAccents() {
        assertEquals("cafe", SearchText.normalize("Café"));
        assertEquals("raton", SearchText.normalize("Ratón"));
        assertEquals("aguila", SearchText.normalize("ÁGUILA"));
        assertEquals("pinon", SearchText.normalize("Piñón"));
    }

    @Test
    @DisplayName("normalize de null es la cadena vacía")
    void normalizeNullIsEmpty() {
        assertEquals("", SearchText.normalize(null));
    }

    @Test
    @DisplayName("La búsqueda vacía o nula no filtra")
    void emptySearchMatchesEverything() {
        assertTrue(SearchText.matches("", "Café", "CAF-001"));
        assertTrue(SearchText.matches(null, "Café", "CAF-001"));
        assertTrue(SearchText.matches("   ", "Café", "CAF-001"));
    }

    @Test
    @DisplayName("La búsqueda ignora mayúsculas y acentos en cualquiera de los campos")
    void searchIsCaseAndAccentInsensitive() {
        assertTrue(SearchText.matches("cafe", "Café molido", "CAF-001"));
        assertTrue(SearchText.matches("CAFÉ", "Cafe molido", "CAF-001"));
        assertTrue(SearchText.matches("caf-001", "Café molido", "CAF-001"));
    }

    @Test
    @DisplayName("No casa si el término no está en ningún campo")
    void doesNotMatchWhenAbsentFromEveryField() {
        assertFalse(SearchText.matches("te", "Café molido", "CAF-001"));
    }

    @Test
    @DisplayName("Un campo null no rompe la comparación")
    void nullFieldsAreTolerated() {
        assertFalse(SearchText.matches("cafe", null, "CAF-001"));
        assertFalse(SearchText.matches("cafe", (String[]) null));
    }
}
