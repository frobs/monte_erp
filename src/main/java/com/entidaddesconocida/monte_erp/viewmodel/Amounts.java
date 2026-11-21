package com.entidaddesconocida.monte_erp.viewmodel;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Single source of truth for the money text in the screens and in their exports
 * (finding B2).
 *
 * <p>Before this helper the tables used
 * {@code String.format(Locale.ROOT, "%.2f €")}, which renders a dot
 * ({@code 1234.56 €}), while the CSV, the PDF and the report footer replaced the
 * dot with a comma ({@code 1234,56 €}). The same amount therefore appeared with
 * two conventions in the same flow. {@link #currency} and {@link #plain} share
 * the rounding and the Spanish decimal comma; {@code currency} only appends the
 * euro sign.</p>
 *
 * <p>The {@link BigDecimal} is scaled directly (never through {@code double})
 * with {@link RoundingMode#HALF_UP}, so {@code 0.005} becomes {@code 0,01} and the
 * result does not depend on the binary representation of the value. There is no
 * thousands separator: the CSV is consumed by other tools and the three
 * representations of the same amount must match exactly.</p>
 *
 * <p>A {@code null} amount has no text representation: both methods return an
 * empty string, which leaves the table cell, the CSV column or the PDF cell
 * blank instead of inventing a zero.</p>
 */
public final class Amounts {

    private static final int SCALE = 2;
    private static final String CURRENCY_SUFFIX = " €";
    private static final char DECIMAL_DOT = '.';
    private static final char DECIMAL_COMMA = ',';

    private Amounts() {
    }

    /** {@code 1234.56} → {@code "1234,56 €"}; {@code null} → {@code ""}. */
    public static String currency(BigDecimal amount) {
        return amount == null ? "" : plain(amount) + CURRENCY_SUFFIX;
    }

    /** {@code 1234.56} → {@code "1234,56"}; {@code null} → {@code ""}. */
    public static String plain(BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        return amount.setScale(SCALE, RoundingMode.HALF_UP)
                .toPlainString()
                .replace(DECIMAL_DOT, DECIMAL_COMMA);
    }
}
