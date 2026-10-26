package com.entidaddesconocida.monte_erp.view;

import com.entidaddesconocida.monte_erp.viewmodel.SearchText;

import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;

import java.util.function.Function;

/**
 * Turns an entity {@link ComboBox} into a <em>searchable</em> one: the popup
 * carries a search field that filters the options as the user types.
 *
 * <p>It is a View-only utility built on top of the ControlSFX
 * {@code SearchableComboBox} skin, already on the classpath, so no new
 * dependency is added. ControlSFX filters the popup by comparing the typed text
 * (lowercased) against the {@code StringConverter} of the combo — falling back
 * to {@code toString()} when there is none — and binds the
 * {@code cellFactory}/{@code buttonCell} of the original control to its internal
 * one, so the custom cells installed here keep working and their label is what
 * the user still sees.</p>
 *
 * <p>The filter is only {@code toLowerCase} (the {@code impl} package is not
 * exported, so its predicate cannot be customized). To make it accent
 * insensitive like the rest of the application (RF-11), {@link #searchableText}
 * appends the {@link SearchText#normalize(String) normalized} alias of the
 * label: {@code "Café"} becomes {@code "Café cafe"}, so typing either spelling
 * finds the row. The alias lives only in the filter text; every combo installed
 * here also gets a button cell, so the alias is never displayed.</p>
 *
 * <p>The pure text helpers ({@link #searchableText}, {@link #converter}) are
 * public and JavaFX-toolkit free so they can be tested with plain JUnit;
 * {@link #install} is the only part that touches the control.</p>
 */
public final class SearchableCombos {

    /** Prompt shown in the popup search field (ControlSFX ships it in English). */
    private static final String SEARCH_PROMPT = "Buscar…";

    /** Style class of the search field created by the ControlSFX skin. */
    private static final String SEARCH_FIELD_SELECTOR = ".combo-box-search";

    private SearchableCombos() {
        // Utility class: never instantiated.
    }

    /**
     * Builds the text that ControlSFX compares against what the user types: the
     * label itself plus its unaccented, lowercased alias.
     *
     * <p>Because the skin only lowercases, both {@code "cafe"} and {@code "café"}
     * match {@code "Café"}: the first through the alias, the second through the
     * label. A {@code null} or blank label (e.g. an empty product label) yields
     * the empty string, which matches nothing.</p>
     *
     * @param label the visible label of an option
     * @return the filterable text, never {@code null}
     */
    public static String searchableText(String label) {
        if (label == null || label.isBlank()) {
            return "";
        }
        return label + " " + SearchText.normalize(label);
    }

    /**
     * Converter whose {@link StringConverter#toString(Object)} is the filterable
     * text of the label, which is what the ControlSFX predicate reads.
     *
     * @param label maps an item (possibly {@code null}, the "all"/"none" option)
     *              to its visible label
     */
    public static <T> StringConverter<T> converter(Function<T, String> label) {
        return new StringConverter<>() {
            @Override
            public String toString(T item) {
                return searchableText(label.apply(item));
            }

            @Override
            public T fromString(String text) {
                // The combos are not editable: the converter is read-only.
                return null;
            }
        };
    }

    /**
     * Makes {@code combo} searchable: installs the filter converter, paints the
     * cells with {@code label} (both the popup rows and the button cell) and
     * localizes the search prompt.
     *
     * <p>The label function is called with {@code null} for the optional
     * "all"/"none" option and must render it (e.g. "Todas las categorías"), and
     * with a real item otherwise. Empty rows get no text.</p>
     *
     * @param combo the combo to make searchable
     * @param label maps an item (or {@code null}) to its visible label
     */
    public static <T> void install(ComboBox<T> combo, Function<T, String> label) {
        combo.setConverter(converter(label));
        combo.setCellFactory(list -> cell(label));
        combo.setButtonCell(cell(label));
        localizeSearchPrompt(combo);
    }

    /** List/button cell that always renders the provided label. */
    private static <T> ListCell<T> cell(Function<T, String> label) {
        return new ListCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                // Checking "empty" keeps the label of the null option (e.g.
                // "Todas las categorías") out of the blank rows below the list.
                setText(empty ? null : label.apply(item));
            }
        };
    }

    /**
     * Replaces the English "Search…" prompt of the internal search field with
     * its Spanish counterpart. The field belongs to the skin (created lazily),
     * so it is looked up the first time the popup is shown.
     */
    private static void localizeSearchPrompt(ComboBox<?> combo) {
        combo.showingProperty().addListener((observable, wasShowing, isShowing) -> {
            if (!isShowing) {
                return;
            }
            Node searchField = combo.lookup(SEARCH_FIELD_SELECTOR);
            if (searchField instanceof TextField field) {
                field.setPromptText(SEARCH_PROMPT);
            }
        });
    }
}
