package com.entidaddesconocida.monte_erp.viewmodel;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.math.BigDecimal;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * Lines (or lots) of a document being typed in, with its running total.
 *
 * <p>It is the part RF-07/RF-13 (sales orders) and RF-14 (purchases) share: an
 * editable list where a product can appear only once (decisions D3/D2), the
 * total is always derived from the lines (never typed) and a line that would
 * push the stored total past the money column limit is rejected.</p>
 *
 * <p>Extracted from the master-detail ViewModels (A2). It knows nothing about
 * JavaFX controls, repositories or messages: the screen validates the form,
 * builds the line and reports the rejection; this class only owns the list, the
 * total and the invariants above.</p>
 *
 * @param <L> line type ({@code SalesOrderLine} for orders, {@code Stock} for purchase lots)
 */
public final class DraftEditor<L> {

    private final ObservableList<L> lines = FXCollections.observableArrayList();
    private final ReadOnlyObjectWrapper<BigDecimal> total =
            new ReadOnlyObjectWrapper<>(BigDecimal.ZERO);
    private final BigDecimal maxTotal;
    private final ToIntFunction<L> productIdOf;
    private final Function<L, BigDecimal> subtotalOf;

    /**
     * @param maxTotal    largest total the database column can store
     * @param productIdOf id of the product of a line ({@code -1} when it has none,
     *                    so a line without product never matches a duplicate
     *                    check)
     * @param subtotalOf  subtotal of a line, already rounded by its own type
     */
    public DraftEditor(BigDecimal maxTotal, ToIntFunction<L> productIdOf,
            Function<L, BigDecimal> subtotalOf) {
        this.maxTotal = maxTotal;
        this.productIdOf = productIdOf;
        this.subtotalOf = subtotalOf;
    }

    /** Live lines of the draft; the view binds its table to it. */
    public ObservableList<L> lines() {
        return lines;
    }

    /** Total of the draft as last published (updated by every add/remove). */
    public ReadOnlyObjectProperty<BigDecimal> totalProperty() {
        return total.getReadOnlyProperty();
    }

    /** Total recomputed from the lines, for the save validation. */
    public BigDecimal total() {
        BigDecimal sum = BigDecimal.ZERO;
        for (L line : lines) {
            sum = sum.add(subtotalOf.apply(line));
        }
        return sum;
    }

    /** {@code true} when no line has been added yet. */
    public boolean isEmpty() {
        return lines.isEmpty();
    }

    /**
     * Adds a line and publishes the new total.
     *
     * @return {@code false} when the line would push the total past the column
     *         limit; the draft is left untouched so the caller can report it
     */
    public boolean add(L line) {
        BigDecimal next = total().add(subtotalOf.apply(line));
        if (next.compareTo(maxTotal) > 0) {
            return false;
        }
        lines.add(line);
        total.set(next);
        return true;
    }

    /**
     * Removes a line and recomputes the total.
     *
     * @return {@code false} when the line was not part of the draft
     */
    public boolean remove(L line) {
        if (line == null || !lines.remove(line)) {
            return false;
        }
        total.set(total());
        return true;
    }

    /** Empties the draft. */
    public void clear() {
        lines.clear();
        total.set(BigDecimal.ZERO);
    }

    /** Whether the draft already has a line for the given product (decision D3/D2). */
    public boolean containsProduct(int productId) {
        return lines.stream().anyMatch(line -> productIdOf.applyAsInt(line) == productId);
    }

    /** Whether the current total fits in the stored money column (checked again on save). */
    public boolean totalWithinLimit() {
        return total().compareTo(maxTotal) <= 0;
    }
}
