package com.entidaddesconocida.monte_erp.domain.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Immutable value object holding the period a report is generated for
 * (RF-12 / CU-09, decision D7).
 *
 * <p>Both bounds are <strong>inclusive</strong> and optional: {@code null} means
 * "no bound", so {@link #all()} represents the whole history. The comparison is
 * done by <strong>day</strong> ({@link #contains(LocalDateTime)}), which is the
 * rule the SQL adapter mirrors with
 * {@code entry_date >= from AND entry_date < to + 1 day}.</p>
 *
 * <p>The canonical constructor rejects an inverted period ({@code from > to}) with
 * an {@link IllegalArgumentException} <em>before</em> any query is issued, so the
 * ViewModel can show a validation message without touching the database.</p>
 *
 * @param from lower bound (inclusive) or {@code null} when there is none
 * @param to   upper bound (inclusive) or {@code null} when there is none
 */
public record ReportPeriod(LocalDate from, LocalDate to) {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final ReportPeriod ALL = new ReportPeriod(null, null);

    /**
     * @throws IllegalArgumentException when both bounds are present and
     *                                  {@code from} is after {@code to}
     */
    public ReportPeriod {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException(
                    "La fecha inicial del período no puede ser posterior a la final: "
                            + from + " > " + to);
        }
    }

    /** Whole history: no lower nor upper bound. */
    public static ReportPeriod all() {
        return ALL;
    }

    /**
     * Builds a period with optional inclusive bounds.
     *
     * @throws IllegalArgumentException when both bounds are present and
     *                                  {@code from} is after {@code to}
     */
    public static ReportPeriod of(LocalDate from, LocalDate to) {
        return from == null && to == null ? ALL : new ReportPeriod(from, to);
    }

    /** Whether the period has no bound at all (the whole history). */
    public boolean isEmpty() {
        return from == null && to == null;
    }

    /**
     * Whether the given moment falls inside the period, comparing by day and
     * including both bounds. A {@code null} moment never belongs to it.
     */
    public boolean contains(LocalDateTime moment) {
        if (moment == null) {
            return false;
        }
        LocalDate day = moment.toLocalDate();
        if (from != null && day.isBefore(from)) {
            return false;
        }
        return to == null || !day.isAfter(to);
    }

    /** Spanish label for the user interface, e.g. {@code "01/01/2025 – 31/03/2025"}. */
    public String label() {
        if (isEmpty()) {
            return "Todo el histórico";
        }
        String fromLabel = from == null ? "…" : DATE_FORMAT.format(from);
        String toLabel = to == null ? "…" : DATE_FORMAT.format(to);
        return fromLabel + " – " + toLabel;
    }

    @Override
    public String toString() {
        return "ReportPeriod{" + label() + '}';
    }
}
