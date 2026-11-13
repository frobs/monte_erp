package com.entidaddesconocida.monte_erp.ui;

import com.entidaddesconocida.monte_erp.ui.support.AbstractUiTest;
import com.entidaddesconocida.monte_erp.ui.support.TempFiles;
import com.entidaddesconocida.monte_erp.ui.support.TestDatabase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CU-09 (RF-12): stock and sales reports, period filter and CSV/PDF export.
 */
@Tag("ui")
class ReportsUiTest extends AbstractUiTest {

    private int receivedLots;
    private int excludedLots;
    private int salesRows;
    private int activeOrders;
    private int cancelledOrders;

    @BeforeEach
    void openReports() {
        receivedLots = (int) TestDatabase.longValue("""
                SELECT COUNT(*) FROM STOCK s
                  JOIN PURCHASE p ON p.id = s.purchase_id
                 WHERE p.status NOT IN ('returned', 'cancelled')
                """);
        excludedLots = (int) TestDatabase.longValue("SELECT COUNT(*) FROM STOCK") - receivedLots;
        salesRows = (int) TestDatabase.longValue(
                "SELECT COUNT(DISTINCT product_id) FROM SALES_ORDER_LINE");
        activeOrders = (int) TestDatabase.longValue("""
                SELECT COUNT(DISTINCT o.id) FROM SALES_ORDER o
                  JOIN SALES_ORDER_LINE l ON l.sales_order_id = o.id
                 WHERE o.status <> 'cancelled'
                """);
        cancelledOrders = (int) TestDatabase.longValue("""
                SELECT COUNT(DISTINCT o.id) FROM SALES_ORDER o
                  JOIN SALES_ORDER_LINE l ON l.sales_order_id = o.id
                 WHERE o.status = 'cancelled'
                """);

        loginAsAdmin();
        ui.clickButton("Informes");
        ui.assertTitle("Monte ERP · Informes");
    }

    @Test
    @DisplayName("Los informes cargan el histórico completo con sus totales")
    void loadsBothReportsWithTotals() {
        ui.assertRowCount("stockLotsTable", 44);
        ui.assertLabel("countLabel", "44 filas");
        ui.assertLabelContains("stockTotalsLabel", "Lotes: " + receivedLots);
        ui.assertLabelContains("stockTotalsLabel", "Excluidos: " + excludedLots);

        ui.selectTab("reportsTabPane", 1);

        ui.assertRowCount("salesTable", salesRows);
        ui.assertLabel("countLabel", salesRows + " filas");
        ui.assertLabelContains("salesTotalsLabel", "Pedidos: " + activeOrders);
        ui.assertLabelContains("salesTotalsLabel", "Pedidos cancelados: " + cancelledOrders);
    }

    @Test
    @DisplayName("El periodo filtra ambos informes y un rango invertido se rechaza")
    void periodFiltersAndValidates() {
        ui.setDate("fromPicker", LocalDate.now().minusDays(30));
        ui.setDate("toPicker", LocalDate.now());
        ui.clickButton("Generar");

        ui.assertLabelEmpty("errorLabel");
        ui.assertTrue("Debe haber lotes en el periodo",
                ui.rowCount("stockLotsTable") > 0);
        ui.assertTrue("El periodo no debe traer todo el histórico",
                ui.rowCount("stockLotsTable") < 44);

        ui.setDate("fromPicker", LocalDate.now());
        ui.setDate("toPicker", LocalDate.now().minusDays(2));
        ui.clickButton("Generar");

        ui.assertLabel("errorLabel",
                "La fecha «desde» no puede ser posterior a la fecha «hasta».");
        ui.assertRowCount("stockLotsTable", 0);

        ui.clickButton("Limpiar");
        ui.assertLabelEmpty("errorLabel");
        ui.assertRowCount("stockLotsTable", 44);
    }

    @Test
    @DisplayName("Exportación del informe de stock a CSV y PDF")
    void exportsStockReport() throws IOException {
        File csv = temporaryFile("informe-stock", ".csv");
        app.fileSaveDialog().setNextFile(csv);
        ui.clickButton("Exportar CSV");
        ui.assertTrue("Debe avisarse de la exportación",
                ui.dialogText().contains("Se ha guardado el informe en:"));
        ui.confirmDialog();

        String content = Files.readString(csv.toPath(), StandardCharsets.UTF_8);
        assertTrue(content.startsWith("\uFEFFProducto;SKU;Entrada"), "Cabecera CSV inesperada");
        assertTrue(content.contains("Lotes;" + receivedLots), "Faltan los totales del CSV");

        File pdf = temporaryFile("informe-stock", ".pdf");
        app.fileSaveDialog().setNextFile(pdf);
        ui.clickButton("Exportar PDF");
        ui.confirmDialog();

        byte[] bytes = Files.readAllBytes(pdf.toPath());
        assertTrue(bytes.length > 500, "El PDF está vacío");
        assertEquals("%PDF", new String(bytes, 0, 4, StandardCharsets.US_ASCII));
        assertTrue(app.fileSaveDialog().titles().contains("Guardar informe CSV"));
        assertTrue(app.fileSaveDialog().titles().contains("Guardar informe PDF"));
    }

    @Test
    @DisplayName("Exportación del informe de ventas a CSV y PDF")
    void exportsSalesReport() throws IOException {
        ui.selectTab("reportsTabPane", 1);

        File csv = temporaryFile("informe-ventas", ".csv");
        app.fileSaveDialog().setNextFile(csv);
        ui.clickButton("Exportar CSV");
        ui.confirmDialog();

        String content = Files.readString(csv.toPath(), StandardCharsets.UTF_8);
        assertTrue(content.startsWith("\uFEFFProducto;SKU;Unidades vendidas"), "Cabecera CSV inesperada");
        assertTrue(content.contains("Pedidos;" + activeOrders));

        File pdf = temporaryFile("informe-ventas", ".pdf");
        app.fileSaveDialog().setNextFile(pdf);
        ui.clickButton("Exportar PDF");
        ui.confirmDialog();

        byte[] bytes = Files.readAllBytes(pdf.toPath());
        assertEquals("%PDF", new String(bytes, 0, 4, StandardCharsets.US_ASCII));
    }

    private File temporaryFile(String name, String extension) throws IOException {
        File file = File.createTempFile(name, extension,
                TempFiles.directory("exports").toFile());
        file.deleteOnExit();
        return file;
    }
}
