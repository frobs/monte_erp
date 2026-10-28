package com.entidaddesconocida.monte_erp.viewmodel;

import com.entidaddesconocida.monte_erp.domain.model.Product;
import com.entidaddesconocida.monte_erp.domain.model.Purchase;
import com.entidaddesconocida.monte_erp.domain.model.SalesReportRow;
import com.entidaddesconocida.monte_erp.domain.model.SalesReportSummary;
import com.entidaddesconocida.monte_erp.domain.model.Stock;
import com.entidaddesconocida.monte_erp.domain.model.StockReportSummary;
import com.entidaddesconocida.monte_erp.domain.service.SalesReportCalculator;
import com.entidaddesconocida.monte_erp.domain.service.StockReportCalculator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ReportCsv} (RF-12 / CU-09, decisions D5/D6): headers,
 * escaping, Spanish decimals, the UTF-8 BOM and the formula-injection hardening.
 */
class ReportCsvTest {

    private static final LocalDateTime ENTRY_DATE = LocalDateTime.of(2025, 1, 15, 10, 30);

    private static Product product(String name, String sku) {
        return new Product(name, sku, null, new BigDecimal("10.00"), 0, null, true);
    }

    private static Stock lot(int id, String name, String sku, int initial, int available,
                             String price) {
        Product product = product(name, sku);
        product.setId(1);
        Purchase purchase = new Purchase(5, null, null, null, null, null, null);
        return new Stock(id, initial, available, new BigDecimal(price), ENTRY_DATE, product, purchase);
    }

    @Test
    @DisplayName("El informe de stock lleva BOM, cabeceras, filas y totales")
    void stockReportHasBomHeadersRowsAndTotals() {
        Stock only = lot(1, "Teclado", "SKU-1", 10, 7, "1.50");
        StockReportSummary summary = StockReportCalculator.summarize(List.of(only));

        String csv = ReportCsv.stockReport(List.of(only), summary);

        assertTrue(csv.startsWith("\uFEFF"));
        List<String> lines = csv.substring(1).lines().toList();
        assertEquals("Producto;SKU;Entrada;Inicial;Disponible;Precio compra;Compra", lines.get(0));
        assertEquals("Teclado;SKU-1;15/01/2025 10:30;10;7;1,50;#5", lines.get(1));
        assertEquals("", lines.get(2));
        assertTrue(lines.contains("Lotes;1"));
        assertTrue(lines.contains("Unidades disponibles;7"));
        assertTrue(lines.contains("Valor del stock;10,50"));
    }

    @Test
    @DisplayName("El informe de ventas lleva BOM, cabeceras, filas y totales")
    void salesReportHasBomHeadersRowsAndTotals() {
        Product product = product("Teclado", "SKU-1");
        product.setId(1);
        SalesReportSummary summary = new SalesReportSummary(
                List.of(new SalesReportRow(product, 3, new BigDecimal("30.00"))),
                2, 3, new BigDecimal("30.00"));

        String csv = ReportCsv.salesReport(summary.rows(), summary);

        assertTrue(csv.startsWith("\uFEFF"));
        List<String> lines = csv.substring(1).lines().toList();
        assertEquals("Producto;SKU;Unidades vendidas;Importe", lines.get(0));
        assertEquals("Teclado;SKU-1;3;30,00", lines.get(1));
        assertTrue(lines.contains("Pedidos;2"));
        assertTrue(lines.contains("Unidades vendidas;3"));
        assertTrue(lines.contains("Facturación;30,00"));
    }

    @Test
    @DisplayName("Una celda con separador, comilla o salto se entrecomilla y dobla")
    void cellsWithSeparatorQuoteOrBreakAreEscaped() {
        assertEquals("\"a;b\"", ReportCsv.escape("a;b"));
        assertEquals("\"di \"\"hola\"\"\"", ReportCsv.escape("di \"hola\""));
        assertEquals("\"linea1\nlinea2\"", ReportCsv.escape("linea1\nlinea2"));
        assertEquals("normal", ReportCsv.escape("normal"));
        assertEquals("", ReportCsv.escape(null));
    }

    @Test
    @DisplayName("Una celda que empieza por = + - @ se neutraliza con apóstrofo (D6)")
    void formulaCellsAreNeutralised() {
        assertEquals("'=SUM(A1)", ReportCsv.escape("=SUM(A1)"));
        assertEquals("'+1+1", ReportCsv.escape("+1+1"));
        assertEquals("'-2+3", ReportCsv.escape("-2+3"));
        assertEquals("'@import", ReportCsv.escape("@import"));
        assertEquals("'\tcmd", ReportCsv.escape("\tcmd"));
    }

    @Test
    @DisplayName("Sin lotes ni filas el CSV mantiene cabeceras y totales a 0")
    void emptyReportKeepsHeadersAndZeroTotals() {
        String csv = ReportCsv.stockReport(List.of(), StockReportSummary.empty());

        List<String> lines = csv.substring(1).lines().toList();
        assertEquals(5, lines.size()); // header + blank + 3 totals
        assertTrue(lines.contains("Lotes;0"));
        assertTrue(lines.contains("Unidades disponibles;0"));
        assertFalse(lines.contains("Valor del stock;"));
        assertTrue(lines.contains("Valor del stock;0,00"));
    }

    @Test
    @DisplayName("Un producto con separador en el nombre sale entrecomillado en el informe")
    void productNameWithSeparatorIsQuotedInTheStockReport() {
        Stock lot = lot(1, "Teclado; grande", "SKU-1", 5, 5, "2.00");

        String csv = ReportCsv.stockReport(List.of(lot),
                StockReportCalculator.summarize(List.of(lot)));

        assertTrue(csv.contains("\"Teclado; grande\";SKU-1"));
    }

    @Test
    @DisplayName("El informe vacío de ventas no revienta y mantiene los totales a 0")
    void emptySalesReportIsWellFormed() {
        String csv = ReportCsv.salesReport(SalesReportCalculator.summarize(List.of()).rows(),
                SalesReportSummary.empty());

        List<String> lines = csv.substring(1).lines().toList();
        assertTrue(lines.contains("Pedidos;0"));
        assertTrue(lines.contains("Facturación;0,00"));
    }
}
