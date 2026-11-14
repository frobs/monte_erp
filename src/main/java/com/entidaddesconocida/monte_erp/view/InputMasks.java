package com.entidaddesconocida.monte_erp.view;

import javafx.scene.control.TextFormatter;

import java.util.function.IntSupplier;

/**
 * Input masks for the typed form fields (quantity, money, phone and email).
 *
 * <p>They are a View concern, not a validation rule: they stop the user from
 * typing characters that could never form a valid value, so the form does not
 * need a round trip to the database to reject {@code "abc"} or {@code "10.999"}.
 * The authoritative rules (required, positive, range, email shape) still live in
 * the ViewModels as per-field error properties; this class only constrains what
 * can be typed.</p>
 *
 * <p>The predicates are pure and public so they can be tested with plain JUnit
 * and no JavaFX toolkit; the {@link TextFormatter} factories are the only UI
 * part and run in the View.</p>
 */
public final class InputMasks {

    private InputMasks() {
        // Utility class: never instantiated.
    }

    /**
     * {@code true} while {@code text} can still become a positive integer: an
     * empty (in progress) value, or up to {@code maxDigits} digits. No sign, so
     * a negative quantity cannot even be typed.
     */
    public static boolean acceptsPartialInteger(String text, int maxDigits) {
        return text.isEmpty() || text.matches("\\d{1," + maxDigits + "}");
    }

    /**
     * {@code true} while {@code text} can still become a positive integer that
     * does not exceed {@code max}: the form does not let the user type more units
     * than the stock available for the chosen product (RF-09). An empty (in
     * progress) value is always accepted so the field can be cleared.
     */
    public static boolean acceptsPartialIntegerWithin(String text, int maxDigits, int max) {
        if (!acceptsPartialInteger(text, maxDigits)) {
            return false;
        }
        if (text.isEmpty()) {
            return true;
        }
        try {
            return Integer.parseInt(text) <= max;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * {@code true} while {@code text} can still become a money amount: up to
     * {@code maxIntegerDigits} digits, an optional separator ({@code .} or
     * {@code ,}, the ViewModel normalises it) and up to {@code decimalPlaces}
     * decimals. No sign, so a negative price cannot even be typed.
     */
    public static boolean acceptsPartialDecimal(String text, int maxIntegerDigits, int decimalPlaces) {
        return text.isEmpty() || text.matches("\\d{0," + maxIntegerDigits
                + "}([.,]\\d{0," + decimalPlaces + "})?");
    }

    /** Digits only, no sign: blocks anything that could not be a quantity. */
    public static TextFormatter<String> positiveInteger(int maxDigits) {
        return new TextFormatter<>(change ->
                (!change.isContentChange() || acceptsPartialInteger(change.getControlNewText(), maxDigits))
                        ? change : null);
    }

    /**
     * Digits only, but never above {@code maxSupplier}, which is read on every
     * change so the cap follows the selected product's stock.
     */
    public static TextFormatter<String> positiveIntegerWithin(int maxDigits, IntSupplier maxSupplier) {
        return new TextFormatter<>(change -> {
            if (!change.isContentChange()) {
                return change;
            }
            return acceptsPartialIntegerWithin(change.getControlNewText(), maxDigits, maxSupplier.getAsInt())
                    ? change : null;
        });
    }

    /** Digits plus a single decimal separator with at most two decimals. */
    public static TextFormatter<String> money(int maxIntegerDigits) {
        return new TextFormatter<>(change ->
                (!change.isContentChange()
                        || acceptsPartialDecimal(change.getControlNewText(), maxIntegerDigits, 2))
                        ? change : null);
    }

    /**
     * {@code true} while {@code text} can still become a phone number: digits
     * and the usual separators ({@code + - ( ) .} and spaces), up to
     * {@code maxLength} characters. Letters cannot be typed, so the field never
     * holds text the ViewModel would reject.
     */
    public static boolean acceptsPartialPhone(String text, int maxLength) {
        return text.length() <= maxLength && text.matches("[0-9+()\\-\\s.]*");
    }

    /**
     * {@code true} while {@code text} can still become an email address: the
     * characters accepted by the pragmatic email rule, at most one {@code @}
     * and up to {@code maxLength} characters. The rest of the format (domain,
     * TLD) is checked by the ViewModel on save, because a half-typed address is
     * still in progress.
     */
    public static boolean acceptsPartialEmail(String text, int maxLength) {
        if (text.length() > maxLength || !text.matches("[A-Za-z0-9._%+\\-@]*")) {
            return false;
        }
        int at = text.indexOf('@');
        return at < 0 || at == text.lastIndexOf('@');
    }

    /** Phone keyboard: digits and separators only, capped at {@code maxLength}. */
    public static TextFormatter<String> phone(int maxLength) {
        return new TextFormatter<>(change ->
                (!change.isContentChange()
                        || acceptsPartialPhone(change.getControlNewText(), maxLength))
                        ? change : null);
    }

    /**
     * Email keyboard: letters, digits, the common local-part symbols and one
     * {@code @}, capped at {@code maxLength}. Spaces are blocked at the source.
     */
    public static TextFormatter<String> email(int maxLength) {
        return new TextFormatter<>(change ->
                (!change.isContentChange()
                        || acceptsPartialEmail(change.getControlNewText(), maxLength))
                        ? change : null);
    }
}
