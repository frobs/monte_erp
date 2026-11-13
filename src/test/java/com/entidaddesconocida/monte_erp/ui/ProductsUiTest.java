package com.entidaddesconocida.monte_erp.ui;

import com.entidaddesconocida.monte_erp.ui.support.AbstractUiTest;
import com.entidaddesconocida.monte_erp.ui.support.SeedFacts;
import com.entidaddesconocida.monte_erp.ui.support.TestDatabase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * CU-03 (RF-03 / RF-11): product catalogue, filters and input masks.
 */
@Tag("ui")
class ProductsUiTest extends AbstractUiTest {

    @BeforeEach
    void openProducts() {
        loginAsAdmin();
        ui.clickButton("Productos");
        ui.assertTitle("Monte ERP · Gestión de productos");
    }

    @Test
    @DisplayName("La tabla muestra los productos activos del seed")
    void listsActiveProducts() {
        ui.assertRowCount("productsTable", SeedFacts.ACTIVE_PRODUCTS);
        ui.assertLabel("countLabel", "Productos: 23");
        ui.assertRowContains("productsTable", "PORT-LEN-T14");
        ui.assertRowContains("productsTable", "Portátil Lenovo ThinkPad E14 Gen 5");
        ui.assertRowContains("productsTable", "Activo");
    }

    @Test
    @DisplayName("Mostrar inactivos añade los productos dados de baja")
    void showsInactiveProducts() {
        ui.setChecked("showInactiveCheck", true);

        ui.assertRowCount("productsTable", SeedFacts.PRODUCTS - SeedFacts.ACTIVE_PRODUCTS);
        ui.assertLabel("countLabel", "Productos: 2");
        ui.assertRowContains("productsTable", "PORT-ASU-B1");
        ui.assertRowContains("productsTable", "MON-SAM-G5");
        ui.assertRowContains("productsTable", "Inactivo");
    }

    @Test
    @DisplayName("Filtros por texto, categoría y proveedor, y limpieza")
    void filtersByTextCategoryAndSupplier() {
        ui.type("searchField", "portatil");
        ui.assertRowCount("productsTable", 3);

        ui.clickButton("Limpiar filtros");
        ui.assertRowCount("productsTable", SeedFacts.ACTIVE_PRODUCTS);

        ui.selectCombo("categoryFilterCombo", "Portátiles");
        ui.assertRowCount("productsTable", 3);
        ui.assertRowContains("productsTable", "PORT-HP-840");

        ui.clickButton("Limpiar filtros");
        ui.selectCombo("supplierFilterCombo", "TechData");
        ui.assertRowCount("productsTable", 7);
        ui.assertRowContains("productsTable", "PORT-HP-840");
        ui.assertRowContains("productsTable", "COM-COR-RM750");
        ui.assertRowContains("productsTable", "SERV-INST-01");

        ui.clickButton("Limpiar filtros");
        ui.assertRowCount("productsTable", SeedFacts.ACTIVE_PRODUCTS);
    }

    @Test
    @DisplayName("Alta de producto con categoría, precio y stock mínimo")
    void createsProduct() {
        ui.clickButton("Nuevo producto");
        ui.assertLabel("formTitleLabel", "Nuevo producto");
        ui.type("nameField", "QA Producto Test");
        ui.type("skuField", "QA-001");
        ui.type("descriptionField", "Producto creado por los tests de interfaz");
        ui.type("priceField", "10.50");
        ui.type("minStockField", "5");
        ui.selectCombo("categoryCombo", "Monitores");
        ui.clickButton("Guardar");

        ui.assertLabel("statusLabel", "Producto guardado correctamente.");
        ui.assertLabel("countLabel", "Productos: 24");
        ui.assertRowContains("productsTable", "QA-001 | QA Producto Test | 10.50 € | Monitores | Activo");
        assertEquals(1L, TestDatabase.longValue(
                "SELECT COUNT(*) FROM PRODUCT WHERE sku = 'QA-001' AND active = 1"));
    }

    @Test
    @DisplayName("Las máscaras impiden teclear precios inválidos")
    void priceMaskRejectsInvalidCharacters() {
        ui.clickButton("Nuevo producto");
        ui.type("priceField", "abc");
        ui.assertTrue("La máscara debe descartar las letras", ui.textOf("priceField").isEmpty());

        ui.type("priceField", "-5");
        ui.assertTrue("La máscara debe descartar el signo",
                !ui.textOf("priceField").contains("-"));

        ui.clickButton("Guardar");
        ui.assertLabelContains("errorLabel", "Introduce el nombre del producto.");
    }

    @Test
    @DisplayName("SKU duplicado y campos obligatorios")
    void rejectsDuplicateSkuAndEmptyFields() {
        ui.clickButton("Nuevo producto");
        ui.type("nameField", "Duplicado");
        ui.type("skuField", "PORT-LEN-T14");
        ui.type("priceField", "1.00");
        ui.clickButton("Guardar");

        ui.assertLabel("errorLabel", "El SKU 'PORT-LEN-T14' ya existe.");
        ui.assertRowCount("productsTable", SeedFacts.ACTIVE_PRODUCTS);
    }

    @Test
    @DisplayName("Baja y reactivación lógica de un producto")
    void deactivatesAndReactivatesProduct() {
        ui.clickButton("Nuevo producto");
        ui.type("nameField", "QA Producto Baja");
        ui.type("skuField", "QA-BAJA");
        ui.type("priceField", "5.00");
        ui.clickButton("Guardar");
        ui.assertRowContains("productsTable", "QA-BAJA");

        ui.selectRow("productsTable", "QA-BAJA");
        ui.clickButton("Dar de baja producto");
        ui.confirmDialog();

        ui.assertLabel("statusLabel", "Producto 'QA Producto Baja' dado de baja.");
        ui.assertNoRowContains("productsTable", "QA-BAJA");
        assertEquals(0L, TestDatabase.longValue(
                "SELECT active FROM PRODUCT WHERE sku = 'QA-BAJA'"));

        ui.setChecked("showInactiveCheck", true);
        ui.assertRowContains("productsTable", "QA-BAJA");
        ui.selectRow("productsTable", "QA-BAJA");
        ui.clickButton("Reactivar producto");

        ui.assertLabel("statusLabel", "Producto 'QA Producto Baja' reactivado.");
        assertEquals(1L, TestDatabase.longValue(
                "SELECT active FROM PRODUCT WHERE sku = 'QA-BAJA'"));
    }

    @Test
    @DisplayName("Un producto con categoría dada de baja no se puede guardar tal cual")
    void productWithInactiveCategoryIsRejected() {
        TestDatabase.execute("UPDATE CATEGORY SET active = 0 WHERE id = 5");
        ui.clickButton("Actualizar");
        ui.selectRow("productsTable", "RED-TPL-SG24");

        ui.clickButton("Guardar");

        ui.assertLabelContains("errorLabel",
                "La categoría seleccionada está dada de baja. Elige otra o déjala sin categoría.");
        assertEquals(1L, TestDatabase.longValue(
                "SELECT active FROM PRODUCT WHERE sku = 'RED-TPL-SG24'"));
    }
}
