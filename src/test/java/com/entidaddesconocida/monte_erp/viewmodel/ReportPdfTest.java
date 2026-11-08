package com.entidaddesconocida.monte_erp.viewmodel;

import com.entidaddesconocida.monte_erp.domain.model.Product;
import com.entidaddesconocida.monte_erp.domain.model.Purchase;
import com.entidaddesconocida.monte_erp.domain.model.ReportPeriod;
import com.entidaddesconocida.monte_erp.domain.model.SalesReportRow;
import com.entidaddesconocida.monte_erp.domain.model.SalesReportSummary;
import com.entidaddesconocida.monte_erp.domain.model.Stock;
import com.entidaddesconocida.monte_erp.domain.model.StockReportSummary;
import com.entidaddesconocida.monte_erp.domain.service.StockReportCalculator;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ReportPdf} (RF-12): the PDF mirrors the CSV, marks the
 * lots of returned/cancelled purchases and leaves them out of the totals.
 *
 * <p>The bytes are read back with the OpenPDF text extractor, so the test checks
 * what the user will actually see instead of a byte pattern.</p>
 */
class ReportPdfTest {

    private static final LocalDateTime ENTRY = LocalDateTime.of(2025, 1, 15, 10, 30);

    private static Product product() {
        Product product = new Product("Teclado", "SKU-1", null, new BigDecimal("10.00"), 0, null, true);
        product.setId(1);
        return product;
    }

    private static Product cancelledOnlyProduct() {
        Product product = new Product("Ratón", "SKU-2", null, new BigDecimal("5.00"), 0, null, true);
        product.setId(2);
        return product;
    }

    private static Stock lot(int id, int initial, int available, String price,
                             String purchaseStatus) {
        Purchase purchase = new Purchase(5, ENTRY, purchaseStatus, BigDecimal.ZERO, null, null,
                List.of());
        return new Stock(id, initial, available, new BigDecimal(price), ENTRY, product(), purchase);
    }

    private static String text(byte[] pdf) {
        assertTrue(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1).startsWith("%PDF-"));
        try (PdfReader reader = new PdfReader(pdf)) {
            return new PdfTextExtractor(reader).getTextFromPage(1);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer el PDF generado", e);
        }
    }

    @Test
    @DisplayName("El PDF de stock lleva título, período, cabeceras y totales")
    void stockReportHasTitlePeriodHeadersAndTotals() {
        Stock received = lot(1, 10, 7, "1.50", Purchase.STATUS_RECEIVED);

        byte[] pdf = ReportPdf.stockReport(List.of(received),
                StockReportCalculator.summarize(List.of(received)), ReportPeriod.all());

        String text = text(pdf);
        assertTrue(text.contains("Informe de stock"));
        assertTrue(text.contains("Período: Todo el histórico"));
        assertTrue(text.contains("Producto"));
        assertTrue(text.contains("Estado compra"));
        assertTrue(text.contains("Motivo"));
        assertTrue(text.contains("Teclado"));
        assertTrue(text.contains("#5"));
        assertTrue(text.contains("Recibida"));
        assertTrue(text.contains("Lotes: 1"));
        assertTrue(text.contains("Excluidos (devueltos o cancelados): 0"));
        assertTrue(text.contains("Unidades disponibles: 7"));
    }

    @Test
    @DisplayName("El período elegido se imprime en el PDF")
    void stockReportPrintsTheSelectedPeriod() {
        ReportPeriod period = ReportPeriod.of(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));

        String text = text(ReportPdf.stockReport(List.of(), StockReportSummary.empty(), period));

        assertTrue(text.contains("Período: 01/01/2025 – 31/01/2025"));
    }

    @Test
    @DisplayName("Las compras devueltas o canceladas salen con su motivo y no suman")
    void returnedAndCancelledLotsAreMarkedAndExcluded() {
        List<Stock> lots = List.of(
                lot(1, 10, 7, "1.50", Purchase.STATUS_RECEIVED),
                lot(2, 5, 0, "9.00", Purchase.STATUS_RETURNED),
                lot(3, 2, 0, "9.00", Purchase.STATUS_CANCELLED));

        String text = text(ReportPdf.stockReport(lots,
                StockReportCalculator.summarize(lots), ReportPeriod.all()));

        assertTrue(text.contains("Devuelta"));
        assertTrue(text.contains("Devuelta al proveedor"));
        assertTrue(text.contains("Cancelada"));
        assertTrue(text.contains("Compra cancelada"));
        assertTrue(text.contains("Lotes: 1"));
        assertTrue(text.contains("Excluidos (devueltos o cancelados): 2"));
        assertTrue(text.contains("Unidades disponibles: 7"));
        assertTrue(text.contains("Valor del stock: 10,50"));
    }

    @Test
    @DisplayName("El informe de stock vacío no revienta y deja los totales a 0")
    void emptyStockReportIsWellFormed() {
        String text = text(ReportPdf.stockReport(null, null, null));

        assertTrue(text.contains("Lotes: 0"));
        assertTrue(text.contains("Unidades disponibles: 0"));
        assertTrue(text.contains("Valor del stock: 0,00"));
    }

    @Test
    @DisplayName("El PDF de ventas marca las cancelaciones y las explica en el motivo")
    void salesReportMarksCancelledOrders() {
        SalesReportSummary summary = new SalesReportSummary(
                List.of(new SalesReportRow(product(), 3, new BigDecimal("30.00"), 2,
                                new BigDecimal("20.00")),
                        new SalesReportRow(cancelledOnlyProduct(), 0, BigDecimal.ZERO, 4,
                                new BigDecimal("40.00"))),
                1, 2, 3, new BigDecimal("30.00"));

        String text = text(ReportPdf.salesReport(summary.rows(), summary, ReportPeriod.all()));

        assertTrue(text.contains("Informe de ventas por producto"));
        assertTrue(text.contains("Unidades canceladas"));
        assertTrue(text.contains("Teclado"));
        assertTrue(text.contains("Parte de los pedidos cancelados"));
        assertTrue(text.contains("Ratón"));
        assertTrue(text.contains("Todos los pedidos cancelados"));
        assertTrue(text.contains("Pedidos cancelados: 2"));
        assertTrue(text.contains("Unidades canceladas: 6"));
        assertTrue(text.contains("Unidades vendidas: 3"));
        assertTrue(text.contains("Facturación: 30,00"));
    }

    @Test
    @DisplayName("El PDF de ventas sin cancelaciones deja el motivo vacío")
    void salesReportWithoutCancellationsHasNoReason() {
        SalesReportSummary summary = new SalesReportSummary(
                List.of(new SalesReportRow(product(), 3, new BigDecimal("30.00"))),
                1, 3, new BigDecimal("30.00"));

        String text = text(ReportPdf.salesReport(summary.rows(), summary, ReportPeriod.all()));

        assertTrue(text.contains("Teclado"));
        assertFalse(text.contains("Parte de los pedidos cancelados"));
        assertFalse(text.contains("Todos los pedidos cancelados"));
    }

    @Test
    @DisplayName("El informe de ventas vacío tampoco revienta")
    void emptySalesReportIsWellFormed() {
        String text = text(ReportPdf.salesReport(null, null, null));

        assertTrue(text.contains("Pedidos: 0"));
        assertTrue(text.contains("Facturación: 0,00"));
    }
}
