package com.entidaddesconocida.monte_erp.domain.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

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
 * <p>The factory rejects an inverted period ({@code from > to}) with an
 * {@link IllegalArgumentException} <em>before</em> any query is issued, so the
 * ViewModel can show a validation message without touching the database.</p>
 */
public final class ReportPeriod {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final ReportPeriod ALL = new ReportPeriod(null, null);

    private final LocalDate from;
    private final LocalDate to;

    private ReportPeriod(LocalDate from, LocalDate to) {
        this.from = from;
        this.to = to;
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
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException(
                    "La fecha inicial del período no puede ser posterior a la final: "
                            + from + " > " + to);
        }
        if (from == null && to == null) {
            return ALL;
        }
        return new ReportPeriod(from, to);
    }

    /** Lower bound (inclusive) or {@code null} when there is none. */
    public LocalDate from() {
        return from;
    }

    /** Upper bound (inclusive) or {@code null} when there is none. */
    public LocalDate to() {
        return to;
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
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ReportPeriod period)) {
            return false;
        }
        return Objects.equals(from, period.from) && Objects.equals(to, period.to);
    }

    @Override
    public int hashCode() {
        return Objects.hash(from, to);
    }

    @Override
    public String toString() {
        return "ReportPeriod{" + label() + '}';
    }
}
