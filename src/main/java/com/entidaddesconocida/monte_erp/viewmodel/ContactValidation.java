package com.entidaddesconocida.monte_erp.viewmodel;

import java.util.regex.Pattern;

/**
 * Field rules shared by the contact forms of customers (RF-06) and suppliers
 * (RF-05).
 *
 * <p>Both screens validate the same optional {@code phone} and {@code email}
 * columns, so the patterns live here instead of being duplicated. The rules are
 * deliberately pragmatic: the email is checked with a local shape (local part
 * {@code @} one or more domain labels {@code .} alphabetic TLD) and the phone
 * with the usual separators, never with a full RFC grammar.</p>
 *
 * <p>The class is JDK-only and has no JavaFX dependency, so the rules can be
 * tested with plain JUnit and reused from any ViewModel.</p>
 */
public final class ContactValidation {

    /**
     * Pragmatic email shape: a non-empty local part, one or more domain labels
     * and an alphabetic TLD of at least two characters. It rejects spaces,
     * consecutive dots, labels starting/ending with a hyphen and trailing dots
     * ({@code a@b..c}, {@code a@b.c.}). Not a full RFC validator.
     */
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9_%+-]+(?:\\.[A-Za-z0-9_%+-]+)*@"
                    + "(?:[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?\\.)+"
                    + "[A-Za-z]{2,}$");

    /** Lenient phone shape: digits plus the usual separators, never letters. */
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[0-9+()\\-\\s.]*$");

    private ContactValidation() {
        // Utility class: never instantiated.
    }

    /**
     * Whether the value has an email shape. The empty string is not an email:
     * callers decide whether the field is optional and skip the rule.
     */
    public static boolean isValidEmail(String value) {
        return value != null && EMAIL_PATTERN.matcher(value).matches();
    }

    /**
     * Whether the value can be a phone number: only digits and the usual
     * separators ({@code + - ( ) .} and spaces). The empty string matches the
     * pattern; callers treat the field as optional.
     */
    public static boolean isValidPhone(String value) {
        return value != null && PHONE_PATTERN.matcher(value).matches();
    }
}
