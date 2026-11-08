package com.entidaddesconocida.monte_erp.viewmodel;

import com.entidaddesconocida.monte_erp.domain.model.Product;
import com.entidaddesconocida.monte_erp.domain.model.Purchase;
import com.entidaddesconocida.monte_erp.domain.model.ReportPeriod;
import com.entidaddesconocida.monte_erp.domain.model.SalesReportRow;
import com.entidaddesconocida.monte_erp.domain.model.SalesReportSummary;
import com.entidaddesconocida.monte_erp.domain.model.Stock;
import com.entidaddesconocida.monte_erp.domain.model.StockReportSummary;
import com.entidaddesconocida.monte_erp.domain.service.StockReportCalculator;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Pure PDF serialiser for the reports (RF-12 / CU-09), the sibling of
 * {@link ReportCsv}.
 *
 * <p>It only builds the bytes: choosing the file and writing it belongs to the
 * view ({@code FileChooser}), so this utility is unit tested without the JavaFX
 * toolkit. The content mirrors the CSV (same columns, same totals) with the same
 * Spanish labels, which are shared through {@link ReportLabels}.</p>
 *
 * <p>The stock report keeps printing every lot, including the ones whose
 * purchase was returned or cancelled: those rows are painted in <strong>red</strong>
 * to state that they no longer count, and the totals leave them out, exactly like
 * the table of the screen. Money is a {@link BigDecimal} formatted with a comma,
 * as in the rest of the application.</p>
 */
public final class ReportPdf {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final Color HEADER_BACKGROUND = new Color(0xE2, 0xE8, 0xF0);
    private static final Color EXCLUDED_BACKGROUND = new Color(0xFD, 0xE2, 0xE1);
    private static final Color SUBTITLE_COLOR = new Color(0x64, 0x74, 0x8B);
    private static final Color BORDER_COLOR = new Color(0xCB, 0xD5, 0xE1);

    private static final Font TITLE_FONT = font(16f, Font.BOLD);
    private static final Font SUBTITLE_FONT = font(9f, Font.NORMAL, SUBTITLE_COLOR);
    private static final Font HEADER_FONT = font(8.5f, Font.BOLD);
    private static final Font CELL_FONT = font(8f, Font.NORMAL);
    private static final Font TOTAL_FONT = font(9.5f, Font.BOLD);

    private ReportPdf() {
        // Utility class: never instantiated.
    }

    /**
     * Builds the stock report: one row per lot (red when the purchase was
     * returned or cancelled) plus the totals at the end. {@code null} lists are
     * treated as empty.
     */
    public static byte[] stockReport(List<Stock> lots, StockReportSummary summary,
                                     ReportPeriod period) {
        StockReportSummary total = summary == null ? StockReportSummary.empty() : summary;
        return write(document -> {
            addTitle(document, "Informe de stock", period);
            PdfPTable table = table(new float[]{2.4f, 1.1f, 1.4f, 0.8f, 0.9f, 1.0f, 0.8f, 1.1f,
                    1.6f});
            headerRow(table, "Producto", "SKU", "Entrada", "Inicial", "Disponible",
                    "P. compra", "Compra", "Estado compra", "Motivo");
            if (lots != null) {
                for (Stock lot : lots) {
                    Product product = lot.getProduct();
                    boolean counts = lot.countsInStockReport();
                    row(table, counts ? null : EXCLUDED_BACKGROUND,
                            product == null ? "" : product.getName(),
                            product == null ? "" : product.getSku(),
                            formatDate(lot.getEntryDate()),
                            String.valueOf(lot.getInitialQuantity()),
                            String.valueOf(lot.getAvailableQuantity()),
                            money(lot.getPurchasePrice()),
                            purchaseLabel(lot.getPurchase()),
                            ReportLabels.purchaseStatus(lot),
                            ReportLabels.availabilityReason(lot));
                }
            }
            document.add(table);
            paragraph(document, "Lotes: " + total.lotCount()
                    + " · Excluidos (devueltos o cancelados): "
                    + StockReportCalculator.excludedCount(lots), TOTAL_FONT);
            paragraph(document, "Unidades disponibles: " + total.totalAvailableUnits()
                    + " · Valor del stock: " + money(total.totalStockValue()), TOTAL_FONT);
        });
    }

    /**
     * Builds the sales report: one row per product (red when the product only has
     * cancelled orders, so it does not count) plus the totals at the end.
     */
    public static byte[] salesReport(List<SalesReportRow> rows, SalesReportSummary summary,
                                     ReportPeriod period) {
        SalesReportSummary total = summary == null ? SalesReportSummary.empty() : summary;
        return write(document -> {
            addTitle(document, "Informe de ventas por producto", period);
            PdfPTable table = table(new float[]{2.8f, 1.2f, 1.3f, 1.3f, 1.4f, 2.0f});
            headerRow(table, "Producto", "SKU", "Unidades vendidas", "Importe",
                    "Unidades canceladas", "Motivo");
            if (rows != null) {
                for (SalesReportRow reportRow : rows) {
                    Product product = reportRow.getProduct();
                    row(table, reportRow.countsInSalesReport() ? null : EXCLUDED_BACKGROUND,
                            product == null ? "" : product.getName(),
                            product == null ? "" : product.getSku(),
                            String.valueOf(reportRow.getUnitsSold()),
                            money(reportRow.getRevenue()),
                            String.valueOf(reportRow.getCancelledUnits()),
                            ReportLabels.salesReason(reportRow));
                }
            }
            document.add(table);
            paragraph(document, "Pedidos: " + total.orderCount()
                    + " · Pedidos cancelados: " + total.cancelledOrderCount()
                    + " · Unidades canceladas: " + total.cancelledUnits(), TOTAL_FONT);
            paragraph(document, "Unidades vendidas: " + total.totalUnits()
                    + " · Facturación: " + money(total.totalRevenue()), TOTAL_FONT);
        });
    }

    /** Body of a report: it receives an already open document, landscape A4. */
    @FunctionalInterface
    private interface PdfBody {

        void write(Document document) throws DocumentException;
    }

    private static byte[] write(PdfBody body) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4.rotate(), 36f, 36f, 36f, 36f);
        try {
            PdfWriter.getInstance(document, out);
            document.open();
            body.write(document);
            document.close();
        } catch (DocumentException e) {
            // Nothing touches the disk here: an in-memory PDF cannot fail for IO
            // reasons, so a DocumentException is a programming error.
            throw new IllegalStateException("No se pudo generar el PDF del informe", e);
        }
        return out.toByteArray();
    }

    private static void addTitle(Document document, String title, ReportPeriod period)
            throws DocumentException {
        Paragraph heading = new Paragraph(title, TITLE_FONT);
        heading.setSpacingAfter(2f);
        document.add(heading);
        Paragraph bounds = new Paragraph("Período: "
                + (period == null ? ReportPeriod.all().label() : period.label()), SUBTITLE_FONT);
        document.add(bounds);
    }

    private static PdfPTable table(float[] widths) throws DocumentException {
        PdfPTable table = new PdfPTable(widths.length);
        table.setWidths(widths);
        table.setWidthPercentage(100f);
        table.setHeaderRows(1);
        table.setSpacingBefore(10f);
        return table;
    }

    private static void headerRow(PdfPTable table, String... headers) {
        for (String header : headers) {
            PdfPCell cell = cell(header, HEADER_FONT, HEADER_BACKGROUND);
            cell.setHorizontalAlignment(Element.ALIGN_LEFT);
            table.addCell(cell);
        }
    }

    private static void row(PdfPTable table, Color background, String... values) {
        for (String value : values) {
            table.addCell(cell(value, CELL_FONT, background));
        }
    }

    private static PdfPCell cell(String value, Font font, Color background) {
        PdfPCell cell = new PdfPCell(new Phrase(value, font));
        cell.setPadding(4f);
        if (background != null) {
            cell.setBackgroundColor(background);
        }
        cell.setBorderColor(BORDER_COLOR);
        return cell;
    }

    private static void paragraph(Document document, String text, Font font)
            throws DocumentException {
        Paragraph paragraph = new Paragraph(text, font);
        paragraph.setSpacingBefore(6f);
        document.add(paragraph);
    }

    private static Font font(float size, int style) {
        return font(size, style, null);
    }

    /**
     * Helvetica in {@code WinAnsi} covers the Spanish accents and the euro sign,
     * and it is one of the 14 standard fonts, so the PDF stays small.
     */
    private static Font font(float size, int style, Color color) {
        Font font = FontFactory.getFont(FontFactory.HELVETICA, BaseFont.WINANSI,
                BaseFont.EMBEDDED, size, style, color);
        return font;
    }

    private static String money(BigDecimal amount) {
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount;
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',') + " €";
    }

    private static String formatDate(LocalDateTime moment) {
        return moment == null ? "" : DATE_FORMAT.format(moment);
    }

    private static String purchaseLabel(Purchase purchase) {
        return purchase == null || purchase.getId() == 0 ? "" : "#" + purchase.getId();
    }
}
