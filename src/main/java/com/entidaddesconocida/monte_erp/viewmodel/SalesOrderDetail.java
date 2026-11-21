package com.entidaddesconocida.monte_erp.viewmodel;

import com.entidaddesconocida.monte_erp.app.BackgroundTasks;
import com.entidaddesconocida.monte_erp.domain.model.SalesOrder;
import com.entidaddesconocida.monte_erp.domain.model.SalesOrderLine;
import com.entidaddesconocida.monte_erp.domain.model.StockIssue;
import com.entidaddesconocida.monte_erp.domain.repository.SalesOrderRepository;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Detail of the order selected in the sales screen: its lines and, for RF-15,
 * the lots it consumed.
 *
 * <p>Extracted from {@link SalesOrdersViewModel} (A2). The two queries run off
 * the JavaFX thread and are guarded by a generation counter: only the newest
 * submission is applied, so a slow answer for a previous order can never paint
 * its detail over the current one.</p>
 */
final class SalesOrderDetail {

    private static final Logger LOGGER = Logger.getLogger(SalesOrderDetail.class.getName());

    private final SalesOrderRepository salesOrderRepository;
    private final BackgroundTasks backgroundTasks;
    private final Feedback feedback;

    private final ObservableList<SalesOrderLine> lines = FXCollections.observableArrayList();
    private final ObservableList<StockIssue> issues = FXCollections.observableArrayList();

    /** Sequence of the detail queries; only the newest submission is applied. */
    private long generation;

    SalesOrderDetail(SalesOrderRepository salesOrderRepository,
            BackgroundTasks backgroundTasks, Feedback feedback) {
        this.salesOrderRepository = salesOrderRepository;
        this.backgroundTasks = backgroundTasks;
        this.feedback = feedback;
    }

    /** Lines of the selected order. */
    ObservableList<SalesOrderLine> lines() {
        return lines;
    }

    /** Lots consumed by the selected order (RF-15). */
    ObservableList<StockIssue> issues() {
        return issues;
    }

    /** Drops the detail; any query in flight belongs to the previous selection. */
    void clear() {
        generation++;
        lines.clear();
        issues.clear();
    }

    /** Loads the detail of {@code order}, or clears it when the order is {@code null}. */
    void load(SalesOrder order) {
        long submission = ++generation;
        if (order == null) {
            lines.clear();
            issues.clear();
            return;
        }
        int orderId = order.getId();
        backgroundTasks.submit(
                () -> new Detail(salesOrderRepository.findLines(orderId),
                        salesOrderRepository.findIssues(orderId)),
                detail -> {
                    if (submission == generation) {
                        lines.setAll(detail.lines());
                        issues.setAll(detail.issues());
                    }
                },
                failure -> {
                    if (submission == generation) {
                        LOGGER.log(Level.SEVERE, "Could not load the order lines", failure);
                        lines.clear();
                        issues.clear();
                        feedback.fail(ViewMessages.DATABASE_ERROR);
                    }
                });
    }

    /** Lines and consumed lots of one order, read together in the background. */
    private record Detail(List<SalesOrderLine> lines, List<StockIssue> issues) {
    }
}
