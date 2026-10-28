package com.entidaddesconocida.monte_erp.viewmodel;

import com.entidaddesconocida.monte_erp.domain.model.Product;
import com.entidaddesconocida.monte_erp.domain.model.Purchase;
import com.entidaddesconocida.monte_erp.domain.model.SalesReportRow;
import com.entidaddesconocida.monte_erp.domain.model.SalesReportSummary;
import com.entidaddesconocida.monte_erp.domain.model.Stock;
import com.entidaddesconocida.monte_erp.domain.model.StockReportSummary;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Pure CSV serialiser for the reports (RF-12 / CU-09, decisions D5/D6).
 *
 * <p>It only builds the text: choosing the file and writing it belongs to the
 * view ({@code FileChooser}), which keeps this utility testable without the
 * JavaFX toolkit. Format decisions:</p>
 * <ul>
 *   <li>semicolon ({@code ;}) as separator and a UTF-8 BOM at the beginning, so
 *       Excel in Spanish opens the columns and the accents correctly;</li>
 *   <li>{@code CRLF} line breaks and Spanish headers (the user facing text);</li>
 *   <li>money with two decimals and a comma (Spanish convention).</li>
 * </ul>
 *
 * <p><strong>Hardening (D6):</strong> a cell starting with {@code = + - @}, a
 * tab or a carriage return is prefixed with an apostrophe, so a value cannot be
 * interpreted as a formula when the file is opened in Excel/LibreOffice
 * (CSV/formula injection).</p>
 */
public final class ReportCsv {

    private static final String BOM = "\uFEFF";
    private static final String SEPARATOR = ";";
    private static final String LINE_BREAK = "\r\n";
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private ReportCsv() {
        // Utility class: never instantiated.
    }

    /** Serialises the stock report: one row per lot plus its totals at the end. */
    public static String stockReport(List<Stock> lots, StockReportSummary summary) {
        StringBuilder csv = new StringBuilder(BOM);
        appendRow(csv, "Producto", "SKU", "Entrada", "Inicial", "Disponible",
                "Precio compra", "Compra");
        if (lots != null) {
            for (Stock lot : lots) {
                Product product = lot.getProduct();
                csv.append(escape(product == null ? "" : product.getName())).append(SEPARATOR);
                csv.append(escape(product == null ? "" : product.getSku())).append(SEPARATOR);
                csv.append(escape(formatDate(lot.getEntryDate()))).append(SEPARATOR);
                csv.append(lot.getInitialQuantity()).append(SEPARATOR);
                csv.append(lot.getAvailableQuantity()).append(SEPARATOR);
                csv.append(escape(money(lot.getPurchasePrice()))).append(SEPARATOR);
                csv.append(escape(purchaseLabel(lot.getPurchase()))).append(LINE_BREAK);
            }
        }
        StockReportSummary total = summary == null ? StockReportSummary.empty() : summary;
        csv.append(LINE_BREAK);
        appendRow(csv, "Lotes", String.valueOf(total.lotCount()));
        appendRow(csv, "Unidades disponibles", String.valueOf(total.totalAvailableUnits()));
        appendRow(csv, "Valor del stock", money(total.totalStockValue()));
        return csv.toString();
    }

    /** Serialises the sales report: one row per product plus its totals at the end. */
    public static String salesReport(List<SalesReportRow> rows, SalesReportSummary summary) {
        StringBuilder csv = new StringBuilder(BOM);
        appendRow(csv, "Producto", "SKU", "Unidades vendidas", "Importe");
        if (rows != null) {
            for (SalesReportRow row : rows) {
                Product product = row.getProduct();
                csv.append(escape(product == null ? "" : product.getName())).append(SEPARATOR);
                csv.append(escape(product == null ? "" : product.getSku())).append(SEPARATOR);
                csv.append(row.getUnitsSold()).append(SEPARATOR);
                csv.append(escape(money(row.getRevenue()))).append(LINE_BREAK);
            }
        }
        SalesReportSummary total = summary == null ? SalesReportSummary.empty() : summary;
        csv.append(LINE_BREAK);
        appendRow(csv, "Pedidos", String.valueOf(total.orderCount()));
        appendRow(csv, "Unidades vendidas", String.valueOf(total.totalUnits()));
        appendRow(csv, "Facturación", money(total.totalRevenue()));
        return csv.toString();
    }

    /**
     * Escapes a cell: quotes it when it contains the separator, a double quote or
     * a line break (doubling the inner quotes) and neutralises a leading formula
     * trigger with an apostrophe (D6).
     */
    static String escape(String value) {
        if (value == null) {
            return "";
        }
        String result = value;
        if (!result.isEmpty() && isFormulaTrigger(result.charAt(0))) {
            result = "'" + result;
        }
        if (result.indexOf(SEPARATOR.charAt(0)) >= 0 || result.indexOf('"') >= 0
                || result.indexOf('\n') >= 0 || result.indexOf('\r') >= 0) {
            result = '"' + result.replace("\"", "\"\"") + '"';
        }
        return result;
    }

    /** Characters that Excel/LibreOffice treat as the start of a formula. */
    private static boolean isFormulaTrigger(char first) {
        return first == '=' || first == '+' || first == '-' || first == '@'
                || first == '\t' || first == '\r';
    }

    private static String money(BigDecimal amount) {
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount;
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    private static String formatDate(LocalDateTime moment) {
        return moment == null ? "" : DATE_FORMAT.format(moment);
    }

    private static String purchaseLabel(Purchase purchase) {
        return purchase == null || purchase.getId() == 0 ? "" : "#" + purchase.getId();
    }

    private static void appendRow(StringBuilder csv, String... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                csv.append(SEPARATOR);
            }
            csv.append(escape(cells[i]));
        }
        csv.append(LINE_BREAK);
    }
}
