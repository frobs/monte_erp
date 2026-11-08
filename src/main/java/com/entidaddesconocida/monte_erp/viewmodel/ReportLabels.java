package com.entidaddesconocida.monte_erp.viewmodel;

import com.entidaddesconocida.monte_erp.domain.model.Purchase;
import com.entidaddesconocida.monte_erp.domain.model.SalesReportRow;
import com.entidaddesconocida.monte_erp.domain.model.Stock;

/**
 * User facing labels shared by both reports (RF-12 / CU-09).
 *
 * <p>The stock report shows one row per lot. A lot with {@code 0} available can
 * be perfectly healthy (it was sold) or not (its purchase was returned or
 * cancelled), so the report prints the <strong>status of the purchase</strong>
 * and fills the reason only when there is something wrong to explain: a purchase
 * <strong>returned</strong> or <strong>cancelled</strong>. Every other state
 * (received, pending, or an available lot) leaves the reason empty, so the column
 * never repeats what the status already says. The lot itself stays in the report
 * even when it is no longer stock; only the totals leave it out
 * ({@link Stock#countsInStockReport()}).</p>
 *
 * <p>The sales report applies the same rule to the cancelled orders of a product:
 * {@link #salesReason(SalesReportRow)} only returns text when the row carries
 * cancelled units, so a product that sold nothing is listed and explained instead
 * of disappearing.</p>
 *
 * <p>It is a stateless utility with no JavaFX and no database, so both the CSV
 * ({@link ReportCsv}), the PDF ({@link ReportPdf}) and the tables of the view
 * ({@code ReportsView}) print exactly the same text, and it is unit tested in
 * isolation.</p>
 */
public final class ReportLabels {

    /** Reason of a lot whose purchase was given back to the supplier. */
    static final String REASON_RETURNED = "Devuelta al proveedor";

    /** Reason of a lot whose purchase was cancelled before arriving. */
    static final String REASON_CANCELLED = "Compra cancelada";

    /** Reason of a product whose only orders were cancelled. */
    static final String REASON_ALL_ORDERS_CANCELLED = "Todos los pedidos cancelados";

    /** Reason of a product that still sold something and also had cancellations. */
    static final String REASON_PARTLY_CANCELLED = "Parte de los pedidos cancelados";

    private ReportLabels() {
        // Utility class: never instantiated.
    }

    /**
     * Spanish label of the status of the purchase the lot comes from ({@code
     * "Pendiente"}, {@code "Recibida"}, {@code "Cancelada"}, {@code "Devuelta"}),
     * or an empty string when the lot carries no purchase/status (a light read
     * model).
     */
    public static String purchaseStatus(Stock lot) {
        if (lot == null || lot.getPurchase() == null) {
            return "";
        }
        String status = lot.getPurchase().getStatus();
        return status == null ? "" : Purchase.labelFor(status);
    }

    /**
     * Explanation of a lot that is no longer stock: the purchase was returned to
     * the supplier or cancelled. Any other lot returns an empty string, so the
     * column stays blank unless there is a real reason to warn about.
     */
    public static String availabilityReason(Stock lot) {
        if (lot == null || lot.getPurchase() == null) {
            return "";
        }
        String status = lot.getPurchase().getStatus();
        if (Purchase.STATUS_RETURNED.equals(status)) {
            return REASON_RETURNED;
        }
        if (Purchase.STATUS_CANCELLED.equals(status)) {
            return REASON_CANCELLED;
        }
        return "";
    }

    /**
     * Explanation of a product row that had cancelled orders: all of them (the
     * row does not count) or only a part. A row without cancellations returns an
     * empty string, so the column stays blank unless there is a real reason to
     * warn about, exactly like {@link #availabilityReason(Stock)} in the stock
     * report.
     */
    public static String salesReason(SalesReportRow row) {
        if (row == null || row.getCancelledUnits() == 0) {
            return "";
        }
        return row.countsInSalesReport() ? REASON_PARTLY_CANCELLED : REASON_ALL_ORDERS_CANCELLED;
    }
}
