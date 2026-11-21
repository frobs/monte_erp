package com.entidaddesconocida.monte_erp.viewmodel;

import com.entidaddesconocida.monte_erp.domain.model.Product;
import com.entidaddesconocida.monte_erp.domain.model.Purchase;
import com.entidaddesconocida.monte_erp.domain.model.PurchaseStatus;
import com.entidaddesconocida.monte_erp.domain.model.ReportPeriod;
import com.entidaddesconocida.monte_erp.domain.model.Stock;
import com.entidaddesconocida.monte_erp.domain.service.StockReportCalculator;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks the money format shared by the tables and the exports (finding B2, T2):
 * the same amount must use the Spanish decimal comma in the screen, in the CSV
 * and in the PDF.
 */
class AmountsTest {

    private static final LocalDateTime ENTRY = LocalDateTime.of(2025, 1, 15, 10, 30);

    @Test
    @DisplayName("El separador decimal es la coma y el símbolo va al final")
    void currencyUsesCommaAndEuroSuffix() {
        assertEquals("1234,56 €", Amounts.currency(new BigDecimal("1234.56")));
        assertEquals("1234,56", Amounts.plain(new BigDecimal("1234.56")));
    }

    @Test
    @DisplayName("Redondea HALF_UP sin pasar por double")
    void roundsHalfUpOnTheDecimalValue() {
        assertEquals("0,01 €", Amounts.currency(new BigDecimal("0.005")));
        assertEquals("2,35 €", Amounts.currency(new BigDecimal("2.345")));
        assertEquals("0,00 €", Amounts.currency(new BigDecimal("0.004")));
        // 1234.565 as a double is 1234.5649999… and would round down.
        assertEquals("1234,57 €", Amounts.currency(new BigDecimal("1234.565")));
    }

    @Test
    @DisplayName("Fija la escala en dos decimales y admite negativos")
    void fixesScaleAndKeepsTheSign() {
        assertEquals("10,00 €", Amounts.currency(new BigDecimal("10")));
        assertEquals("10,50 €", Amounts.currency(new BigDecimal("10.5")));
        assertEquals("-1234,50 €", Amounts.currency(new BigDecimal("-1234.5")));
    }

    @Test
    @DisplayName("Un importe nulo se queda vacío en vez de inventar un cero")
    void nullAmountIsBlank() {
        assertTrue(Amounts.currency(null).isEmpty());
        assertTrue(Amounts.plain(null).isEmpty());
    }

    @Test
    @DisplayName("No hay separador de miles, para que tabla, CSV y PDF coincidan")
    void neverAddsThousandsSeparator() {
        assertEquals("1234567,89 €", Amounts.currency(new BigDecimal("1234567.89")));
    }

    @Test
    @DisplayName("El mismo importe sale con coma en la tabla, el CSV y el PDF")
    void sameAmountInTableCsvAndPdf() {
        Stock lot = stockLot("1234.56");
        List<Stock> lots = List.of(lot);

        String table = Amounts.currency(lot.getPurchasePrice());
        String csv = ReportCsv.stockReport(lots, StockReportCalculator.summarize(lots));
        String pdf = pdfText(ReportPdf.stockReport(lots,
                StockReportCalculator.summarize(lots), ReportPeriod.all()));

        assertEquals("1234,56 €", table);
        assertTrue(csv.contains("1234,56"));
        assertTrue(pdf.contains("1234,56 €"));
        assertFalse(csv.contains("1234.56"));
        assertFalse(pdf.contains("1234.56"));
    }

    private static Stock stockLot(String price) {
        Product product = new Product("Teclado", "SKU-1", null, new BigDecimal("10.00"), 0, null, true);
        product.setId(1);
        Purchase purchase = new Purchase(5, ENTRY, PurchaseStatus.RECEIVED, BigDecimal.ZERO,
                null, null, List.of());
        return new Stock(1, 1, 1, new BigDecimal(price), ENTRY, product, purchase);
    }

    private static String pdfText(byte[] pdf) {
        try (PdfReader reader = new PdfReader(pdf)) {
            return new PdfTextExtractor(reader).getTextFromPage(1);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer el PDF generado", e);
        }
    }
}
