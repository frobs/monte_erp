package com.entidaddesconocida.monte_erp.ui;

import com.entidaddesconocida.monte_erp.ui.support.AbstractUiTest;
import com.entidaddesconocida.monte_erp.ui.support.SeedFacts;
import com.entidaddesconocida.monte_erp.ui.support.TestDatabase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CU-07 / CU-08 / CU-11 (RF-07, RF-08, RF-13, RF-15): sales orders, FIFO
 * consumption, detail and status changes.
 */
@Tag("ui")
class SalesOrdersUiTest extends AbstractUiTest {

    @BeforeEach
    void openSalesOrders() {
        loginAsAdmin();
        ui.clickButton("Pedidos");
        ui.assertTitle("Monte ERP · Gestión de pedidos");
    }

    @Test
    @DisplayName("El histórico muestra los pedidos del seed y sus filtros")
    void listsSeedOrders() {
        ui.assertRowCount("ordersTable", SeedFacts.SALES_ORDERS);
        ui.assertLabel("countLabel", "30 pedidos");
        ui.assertRowContains("ordersTable", "#1 |");
        ui.assertRowContains("ordersTable", "Entregado");

        ui.selectCombo("statusFilterCombo", "Pendiente");
        ui.assertRowCount("ordersTable", SeedFacts.PENDING_ORDERS);
        ui.assertRowContains("ordersTable", "Pendiente");

        ui.clickButton("Limpiar filtros");
        ui.assertRowCount("ordersTable", SeedFacts.SALES_ORDERS);

        ui.selectCombo("customerFilterCombo", "Ayuntamiento de Getafe");
        ui.assertRowCount("ordersTable", 3);
    }

    @Test
    @DisplayName("Un rango de fechas invertido se rechaza")
    void invertedPeriodIsRejected() {
        ui.setDate("fromDatePicker", LocalDate.now());
        ui.setDate("toDatePicker", LocalDate.now().minusDays(1));

        ui.assertLabel("errorLabel",
                "La fecha «desde» no puede ser posterior a la fecha «hasta».");
    }

    @Test
    @DisplayName("Seleccionar un pedido muestra sus líneas y los lotes consumidos")
    void showsOrderDetailAndFifoIssues() {
        ui.selectRow("ordersTable", "#6");

        ui.assertRowCount("orderLinesTable", 4);
        ui.assertRowContains("orderLinesTable", "PORT-LEN-T14");
        ui.assertRowCount("consumedLotsTable", 4);
        ui.assertRowContains("consumedLotsTable", "PORT-LEN-T14");
        ui.assertRowContains("consumedLotsTable", "#1");
    }

    @Test
    @DisplayName("Alta de pedido: cálculo del total, consumo FIFO y guardado")
    void createsOrderAndConsumesStock() {
        int availableBefore = availableUnits("COM-COR-RM750");
        ui.selectCombo("customerCombo", "Ayuntamiento de Getafe");
        ui.selectCombo("productCombo", "COM-COR-RM750");
        ui.type("quantityField", "2");
        ui.clickButton("Añadir línea");

        ui.assertLabel("statusLabel", "Línea añadida.");
        ui.assertRowCount("draftLinesTable", 1);
        ui.assertLabel("totalLabel", "Total: 199,80 €");

        ui.clickButton("Guardar pedido");

        ui.assertLabel("statusLabel", "Pedido #31 guardado correctamente.");
        ui.assertRowCount("ordersTable", SeedFacts.SALES_ORDERS + 1);
        ui.assertRowContains("ordersTable", "#31 |");
        ui.assertRowContains("ordersTable", "Pendiente");
        assertEquals(1L, TestDatabase.longValue(
                "SELECT COUNT(*) FROM SALES_ORDER WHERE id = 31 AND status = 'pending'"));
        assertEquals(1L, TestDatabase.longValue("""
                SELECT COUNT(*) FROM STOCK_ISSUE si
                  JOIN SALES_ORDER_LINE l ON l.id = si.sales_order_line_id
                 WHERE l.sales_order_id = 31
                """));
        assertEquals(availableBefore - 2, availableUnits("COM-COR-RM750"));
    }

    @Test
    @DisplayName("El mismo producto no puede repetirse en un pedido")
    void rejectsDuplicatedLine() {
        ui.selectCombo("customerCombo", "Ayuntamiento de Getafe");
        ui.selectCombo("productCombo", "COM-COR-RM750");
        ui.type("quantityField", "1");
        ui.clickButton("Añadir línea");

        ui.selectCombo("productCombo", "COM-COR-RM750");
        ui.type("quantityField", "1");
        ui.clickButton("Añadir línea");

        ui.assertLabel("errorLabel", "El producto ya está en el pedido.");
        ui.assertRowCount("draftLinesTable", 1);
    }

    @Test
    @DisplayName("Guardar sin cliente ni líneas muestra ambos errores")
    void saveRequiresCustomerAndLines() {
        ui.clickButton("Guardar pedido");

        ui.assertLabelContains("errorLabel", "Selecciona un cliente.");
        ui.assertLabelContains("errorLabel", "Añade al menos una línea al pedido.");
    }

    @Test
    @DisplayName("La cantidad no puede superar el stock disponible del producto")
    void quantityIsCappedByAvailableStock() {
        ui.selectCombo("productCombo", "COM-COR-RM750");
        ui.assertLabelContains("stockLabel", "Disponibles: ");

        ui.type("quantityField", "999");
        ui.assertTrue("La máscara debe impedir teclear más unidades que el stock",
                ui.textOf("quantityField").isEmpty());

        ui.type("quantityField", String.valueOf(availableUnits("COM-COR-RM750")));
        ui.assertTrue("La cantidad disponible sí se puede teclear",
                !ui.textOf("quantityField").isEmpty());
    }

    @Test
    @DisplayName("Transiciones de estado: pendiente → enviado → entregado")
    void changesStatusUntilTerminal() {
        ui.clickTableAction("ordersTable", "#28", "Marcar enviado");
        ui.assertLabel("statusLabel", "Pedido #28 marcado como Enviado.");
        assertEquals("shipped", TestDatabase.stringValue(
                "SELECT status FROM SALES_ORDER WHERE id = 28"));

        ui.clickTableAction("ordersTable", "#28", "Marcar entregado");
        ui.assertLabel("statusLabel", "Pedido #28 marcado como Entregado.");
        assertEquals("delivered", TestDatabase.stringValue(
                "SELECT status FROM SALES_ORDER WHERE id = 28"));

        ui.assertNoTableAction("ordersTable", "#28", "Marcar enviado");
        ui.assertNoTableAction("ordersTable", "#28", "Marcar entregado");
        ui.assertNoTableAction("ordersTable", "#28", "Cancelar");
    }

    @Test
    @DisplayName("Cancelar un pedido devuelve el stock consumido (FIFO)")
    void cancellingOrderRestoresStock() {
        long issuesBefore = TestDatabase.longValue("""
                SELECT COUNT(*) FROM STOCK_ISSUE si
                  JOIN SALES_ORDER_LINE l ON l.id = si.sales_order_line_id
                 WHERE l.sales_order_id = 30
                """);
        long lotBefore = TestDatabase.longValue(
                "SELECT available_quantity FROM STOCK WHERE id = 14");

        ui.clickTableAction("ordersTable", "#30", "Cancelar");
        ui.confirmDialog();

        ui.assertLabel("statusLabel", "Pedido #30 marcado como Cancelado.");
        assertEquals("cancelled", TestDatabase.stringValue(
                "SELECT status FROM SALES_ORDER WHERE id = 30"));
        assertEquals(0L, TestDatabase.longValue("""
                SELECT COUNT(*) FROM STOCK_ISSUE si
                  JOIN SALES_ORDER_LINE l ON l.id = si.sales_order_line_id
                 WHERE l.sales_order_id = 30
                """));
        assertEquals(lotBefore + 2, TestDatabase.longValue(
                "SELECT available_quantity FROM STOCK WHERE id = 14"));
        assertTrue(issuesBefore > 0, "El pedido de prueba debe haber consumido lotes");
    }

    private int availableUnits(String sku) {
        return (int) TestDatabase.longValue("""
                SELECT COALESCE(SUM(s.available_quantity), 0)
                  FROM STOCK s
                  JOIN PRODUCT p ON p.id = s.product_id
                 WHERE p.sku = '%s'
                """.formatted(sku));
    }
}
