package com.entidaddesconocida.monte_erp.domain.service;

import com.entidaddesconocida.monte_erp.domain.model.SalesOrderLine;
import com.entidaddesconocida.monte_erp.domain.model.Stock;
import com.entidaddesconocida.monte_erp.domain.model.StockIssue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pure FIFO allocation of a sales line over the available lots (RF-09 / RF-15,
 * decision D5).
 *
 * <p>It is the <strong>single</strong> implementation of the allocation rule,
 * shared by the ViewModel (to warn before saving) and by the repository (the
 * authority, inside the transaction). Being a pure service it neither touches the
 * database nor mutates the lots: {@link #allocate} returns the {@link StockIssue}
 * list and the caller applies {@link Stock#consume(int)} once the transaction is
 * confirmed.</p>
 *
 * <p>Order: oldest entry date first, breaking ties by lot id; a lot with no
 * available units is ignored. The allocation is <strong>all or nothing</strong>:
 * if the lots do not cover the line, it throws
 * {@link InsufficientStockException} and returns no partial assignments.</p>
 */
public final class FifoAllocator {

    /** Oldest first, ties by id: the FIFO order backed by idx_stock_product_date. */
    private static final Comparator<Stock> FIFO_ORDER =
            Comparator.comparing(Stock::getEntryDate,
                            Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparingInt(Stock::getId);

    private FifoAllocator() {
        // Utility class: never instantiated.
    }

    /**
     * Splits {@code line.quantity} over the available lots, oldest first. The
     * lots are not mutated.
     *
     * @throws InsufficientStockException when the available total is smaller than
     *                                    the line quantity (nothing is returned)
     */
    public static List<StockIssue> allocate(SalesOrderLine line, List<Stock> availableLots)
            throws InsufficientStockException {
        List<Stock> ordered = new ArrayList<>(availableLots == null ? List.of() : availableLots);
        ordered.sort(FIFO_ORDER);

        int remaining = line.getQuantity();
        List<StockIssue> issues = new ArrayList<>();
        for (Stock lot : ordered) {
            if (remaining == 0) {
                break;
            }
            if (lot.getAvailableQuantity() <= 0) {
                continue;
            }
            int taken = Math.min(remaining, lot.getAvailableQuantity());
            issues.add(new StockIssue(taken, line, lot));
            remaining -= taken;
        }
        if (remaining > 0) {
            throw new InsufficientStockException(productName(line), line.getQuantity(),
                    availableTotal(availableLots));
        }
        return issues;
    }

    /** Sum of the available quantities of the lots (0 when the list is empty). */
    public static int availableTotal(List<Stock> lots) {
        if (lots == null) {
            return 0;
        }
        int total = 0;
        for (Stock lot : lots) {
            if (lot.getAvailableQuantity() > 0) {
                total += lot.getAvailableQuantity();
            }
        }
        return total;
    }

    private static String productName(SalesOrderLine line) {
        return line.getProduct() == null ? "?" : line.getProduct().getName();
    }
}
