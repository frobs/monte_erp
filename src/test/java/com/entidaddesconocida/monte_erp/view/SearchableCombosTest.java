package com.entidaddesconocida.monte_erp.view;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javafx.util.StringConverter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the pure text helpers of {@link SearchableCombos}.
 *
 * <p>Only {@code searchableText} and {@code converter} are exercised (both need
 * no JavaFX toolkit); {@code install} is the UI part and is not built here. The
 * assertions reproduce what the ControlSFX skin does: lowercase the typed text
 * and check that the converted item text contains all words.</p>
 */
class SearchableCombosTest {

    @Test
    @DisplayName("El texto filtrable repite la etiqueta y añade su alias sin acentos")
    void searchableTextKeepsLabelAndAddsNormalizedAlias() {
        assertEquals("Café cafe", SearchableCombos.searchableText("Café"));
        assertEquals("Ratón raton", SearchableCombos.searchableText("Ratón"));
    }

    @Test
    @DisplayName("La etiqueta nula o vacía no produce texto filtrable")
    void nullOrBlankLabelIsEmpty() {
        assertEquals("", SearchableCombos.searchableText(null));
        assertEquals("", SearchableCombos.searchableText(""));
        assertEquals("", SearchableCombos.searchableText("   "));
    }

    @Test
    @DisplayName("La búsqueda casa con y sin acentos, en cualquier caso")
    void filterMatchesWithAndWithoutAccents() {
        String filterable = SearchableCombos.searchableText("Café molido").toLowerCase();

        assertTrue(filterable.contains("cafe"));
        assertTrue(filterable.contains("café"));
        assertTrue(filterable.contains("molido"));
    }

    @Test
    @DisplayName("La búsqueda multipalabra encuentra todas las palabras por separado")
    void multiWordFilterFindsEveryWord() {
        String filterable = SearchableCombos.searchableText("Café molido (CAF-001)").toLowerCase();

        assertTrue(filterable.contains("cafe"));
        assertTrue(filterable.contains("molido"));
        assertTrue(filterable.contains("caf-001"));
    }

    @Test
    @DisplayName("El converter devuelve el alias filtrable y tolera el item nulo")
    void converterUsesSearchableTextAndToleratesNull() {
        StringConverter<String> converter = SearchableCombos.converter(item -> item);

        assertEquals("Café cafe", converter.toString("Café"));
        assertEquals("", converter.toString(null));
    }

    @Test
    @DisplayName("El converter usa la etiqueta de la opción nula (Todas/Sin categoría)")
    void converterUsesNullLabelForNullOption() {
        StringConverter<String> converter =
                SearchableCombos.converter(item -> item == null ? "Todas las categorías" : item);

        assertEquals("Todas las categorías todas las categorias", converter.toString(null));
    }
}
