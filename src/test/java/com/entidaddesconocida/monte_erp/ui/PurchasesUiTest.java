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

/**
 * CU-10 (RF-14): purchase history, registration and stock lifecycle
 * (pending → received → returned/cancelled).
 */
@Tag("ui")
class PurchasesUiTest extends AbstractUiTest {

    @BeforeEach
    void openPurchases() {
        loginAsAdmin();
        ui.clickButton("Compras");
        ui.assertTitle("Monte ERP · Gestión de compras");
    }

    @Test
    @DisplayName("El histórico muestra las compras del seed y sus filtros")
    void listsSeedPurchases() {
        ui.assertRowCount("purchasesTable", SeedFacts.PURCHASES);
        ui.assertLabel("countLabel", "18 compras");
        ui.assertRowContains("purchasesTable", "#1 |");
        ui.assertRowContains("purchasesTable", "Recibida");
        ui.assertRowContains("purchasesTable", "Devuelta");

        ui.selectCombo("statusFilterCombo", "Pendiente");
        ui.assertRowCount("purchasesTable", SeedFacts.PENDING_PURCHASES);
        ui.assertRowContains("purchasesTable", "Pendiente");

        ui.clickButton("Limpiar filtros");
        ui.assertRowCount("purchasesTable", SeedFacts.PURCHASES);

        ui.selectCombo("supplierFilterCombo", "TechData");
        ui.assertRowCount("purchasesTable", 3);
    }

    @Test
    @DisplayName("Un rango de fechas invertido se rechaza")
    void invertedPeriodIsRejected() {
        ui.setDate("fromDatePicker", LocalDate.now());
        ui.setDate("toDatePicker", LocalDate.now().minusDays(1));

        ui.assertLabel("errorLabel",
                "La fecha «desde» no puede ser posterior a la fecha «hasta».");

        ui.setDate("fromDatePicker", null);
        ui.setDate("toDatePicker", null);
        ui.assertLabelEmpty("errorLabel");
    }

    @Test
    @DisplayName("Alta de compra con lotes, recepción y efecto en el stock")
    void createsAndReceivesPurchase() {
        ui.clickButton("Nueva compra");
        ui.selectCombo("supplierCombo", "TechData");
        ui.selectCombo("productCombo", "COM-COR-RM750");
        ui.type("quantityField", "4");
        ui.type("purchasePriceField", "50.00");
        ui.clickButton("Añadir lote");

        ui.assertLabel("statusLabel", "Lote añadido.");
        ui.assertRowCount("draftLotsTable", 1);
        ui.assertLabel("totalLabel", "Total: 200,00 €");

        ui.clickButton("Guardar compra");

        ui.assertLabel("statusLabel", "Compra #19 guardada correctamente.");
        ui.assertRowCount("purchasesTable", SeedFacts.PURCHASES + 1);
        ui.assertRowContains("purchasesTable", "#19 |");
        ui.assertRowContains("purchasesTable", "Pendiente");
        assertEquals(1L, TestDatabase.longValue(
                "SELECT COUNT(*) FROM PURCHASE WHERE id = 19 AND status = 'pending'"));
        assertEquals(0L, TestDatabase.longValue(
                "SELECT available_quantity FROM STOCK WHERE purchase_id = 19"));

        ui.clickTableAction("purchasesTable", "#19", "Recibir");

        ui.assertLabel("statusLabel", "Compra #19 marcada como Recibida.");
        ui.assertRowContains("purchasesTable", "Recibida");
        assertEquals(4L, TestDatabase.longValue(
                "SELECT available_quantity FROM STOCK WHERE purchase_id = 19"));
    }

    @Test
    @DisplayName("El mismo producto no puede repetirse en una compra")
    void rejectsDuplicatedProduct() {
        ui.clickButton("Nueva compra");
        ui.selectCombo("supplierCombo", "TechData");
        ui.selectCombo("productCombo", "COM-COR-RM750");
        ui.type("quantityField", "1");
        ui.type("purchasePriceField", "10.00");
        ui.clickButton("Añadir lote");

        ui.selectCombo("productCombo", "COM-COR-RM750");
        ui.type("quantityField", "2");
        ui.type("purchasePriceField", "10.00");
        ui.clickButton("Añadir lote");

        ui.assertLabel("errorLabel", "El producto ya está en la compra.");
        ui.assertRowCount("draftLotsTable", 1);
    }

    @Test
    @DisplayName("Guardar sin proveedor ni lotes muestra ambos errores")
    void saveRequiresSupplierAndLots() {
        ui.clickButton("Nueva compra");
        ui.clickButton("Guardar compra");

        ui.assertLabelContains("errorLabel", "Selecciona un proveedor.");
        ui.assertLabelContains("errorLabel", "Añade al menos un lote a la compra.");
        ui.assertLabelEmpty("statusLabel");
    }

    @Test
    @DisplayName("Recibir una compra pendiente hace disponible su stock")
    void receivesPendingPurchase() {
        ui.clickTableAction("purchasesTable", "#15", "Recibir");

        ui.assertLabel("statusLabel", "Compra #15 marcada como Recibida.");
        assertEquals("received", TestDatabase.stringValue(
                "SELECT status FROM PURCHASE WHERE id = 15"));
        assertEquals(TestDatabase.longValue(
                        "SELECT SUM(initial_quantity) FROM STOCK WHERE purchase_id = 15"),
                TestDatabase.longValue(
                        "SELECT SUM(available_quantity) FROM STOCK WHERE purchase_id = 15"));
    }

    @Test
    @DisplayName("Cancelar una compra pendiente la deja sin stock")
    void cancelsPendingPurchase() {
        ui.clickTableAction("purchasesTable", "#16", "Cancelar");
        ui.confirmDialog();

        ui.assertLabel("statusLabel", "Compra #16 marcada como Cancelada.");
        assertEquals("cancelled", TestDatabase.stringValue(
                "SELECT status FROM PURCHASE WHERE id = 16"));
        assertEquals(0L, TestDatabase.longValue(
                "SELECT SUM(available_quantity) FROM STOCK WHERE purchase_id = 16"));
        ui.assertNoTableAction("purchasesTable", "#16", "Cancelar");
    }

    @Test
    @DisplayName("Devolver una compra recibida vacía sus lotes")
    void returnsReceivedPurchase() {
        ui.clickTableAction("purchasesTable", "#14", "Devolver");
        ui.confirmDialog();

        ui.assertLabel("statusLabel", "Compra #14 marcada como Devuelta.");
        assertEquals("returned", TestDatabase.stringValue(
                "SELECT status FROM PURCHASE WHERE id = 14"));
        assertEquals(0L, TestDatabase.longValue(
                "SELECT SUM(available_quantity) FROM STOCK WHERE purchase_id = 14"));
    }

    @Test
    @DisplayName("No se puede devolver una compra cuyo stock ya se vendió")
    void cannotReturnConsumedPurchase() {
        ui.clickTableAction("purchasesTable", "#" + SeedFacts.FULLY_CONSUMED_PURCHASE_ID,
                "Devolver");
        ui.confirmDialog();

        ui.assertLabelContains("errorLabel", "No se puede devolver la compra #6:");
        ui.assertLabelContains("errorLabel", "unidades ya se han vendido.");
        assertEquals("received", TestDatabase.stringValue(
                "SELECT status FROM PURCHASE WHERE id = 6"));
    }
}
