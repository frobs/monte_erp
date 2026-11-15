package com.entidaddesconocida.monte_erp.domain.service;

import com.entidaddesconocida.monte_erp.domain.model.OrderStatus;
import com.entidaddesconocida.monte_erp.domain.model.Product;
import com.entidaddesconocida.monte_erp.domain.model.SalesReportLine;
import com.entidaddesconocida.monte_erp.domain.model.SalesReportRow;
import com.entidaddesconocida.monte_erp.domain.model.SalesReportSummary;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure domain service that aggregates the sales report (RF-12 / CU-09,
 * decisions D2/D3/D9).
 *
 * <p>It receives the flat lines the adapter read (no {@code GROUP BY} of business
 * in SQL), groups them by product and sums units and amounts with
 * {@link BigDecimal}. The result is ordered by revenue descending and, on a tie,
 * by product name.</p>
 *
 * <p>The lines of a <strong>cancelled</strong> order never add to the units sold
 * nor to the revenue: their stock was given back (RF-08), so counting them would
 * inflate the report (decision D3). They are not thrown away either: they are
 * accumulated in the row as {@link SalesReportRow#getCancelledUnits()} so the
 * report can show, next to the product, why it sold nothing instead of hiding the
 * product completely (the same treatment the stock report gives to the lots of a
 * returned/cancelled purchase).</p>
 *
 * <p>Like {@link FifoAllocator} it is a stateless utility: no database, no
 * JavaFX, no side effects, so its invariants are unit tested without any
 * infrastructure.</p>
 */
public final class SalesReportCalculator {

    private SalesReportCalculator() {
        // Utility class: never instantiated.
    }

    /**
     * Aggregates the lines into a summary. {@code null} or an empty list yields
     * an empty summary with zero totals.
     */
    public static SalesReportSummary summarize(List<SalesReportLine> lines) {
        if (lines == null || lines.isEmpty()) {
            return SalesReportSummary.empty();
        }
        Map<Integer, Accumulator> byProduct = new LinkedHashMap<>();
        Set<Integer> orders = new HashSet<>();
        Set<Integer> cancelledOrders = new HashSet<>();
        for (SalesReportLine line : lines) {
            Product product = line.getProduct();
            Accumulator accumulator = byProduct.computeIfAbsent(product.getId(),
                    key -> new Accumulator(product));
            if (line.getStatus() == OrderStatus.CANCELLED) {
                accumulator.cancelledUnits += line.getQuantity();
                accumulator.cancelledRevenue = accumulator.cancelledRevenue.add(line.subtotal());
                cancelledOrders.add(line.getOrderId());
                continue;
            }
            accumulator.unitsSold += line.getQuantity();
            accumulator.revenue = accumulator.revenue.add(line.subtotal());
            orders.add(line.getOrderId());
        }
        List<SalesReportRow> rows = new ArrayList<>();
        for (Accumulator accumulator : byProduct.values()) {
            rows.add(new SalesReportRow(accumulator.product, accumulator.unitsSold,
                    accumulator.revenue, accumulator.cancelledUnits,
                    accumulator.cancelledRevenue));
        }
        rows.sort(Comparator.comparing(SalesReportRow::getRevenue).reversed()
                .thenComparing(row -> row.getProduct().getName(),
                        Comparator.nullsLast(String::compareToIgnoreCase)));
        return new SalesReportSummary(rows, orders.size(), cancelledOrders.size(),
                rows.stream().mapToInt(SalesReportRow::getUnitsSold).sum(),
                rows.stream().map(SalesReportRow::getRevenue).reduce(BigDecimal.ZERO,
                        BigDecimal::add));
    }

    /** Mutable accumulator used while grouping; discarded before returning. */
    private static final class Accumulator {

        private final Product product;
        private int unitsSold;
        private BigDecimal revenue = BigDecimal.ZERO;
        private int cancelledUnits;
        private BigDecimal cancelledRevenue = BigDecimal.ZERO;

        private Accumulator(Product product) {
            this.product = product;
        }
    }
}
