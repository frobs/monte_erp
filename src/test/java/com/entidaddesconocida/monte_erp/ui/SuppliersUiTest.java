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
 * CU-05 (RF-05): supplier management.
 */
@Tag("ui")
class SuppliersUiTest extends AbstractUiTest {

    @BeforeEach
    void openSuppliers() {
        loginAsAdmin();
        ui.clickButton("Proveedores");
        ui.assertTitle("Monte ERP · Gestión de proveedores");
    }

    @Test
    @DisplayName("La tabla muestra los proveedores activos del seed")
    void listsActiveSuppliers() {
        ui.assertRowCount("suppliersTable", SeedFacts.ACTIVE_SUPPLIERS);
        ui.assertLabel("countLabel", "Proveedores: 6");
        ui.assertRowContains("suppliersTable", "TechData Ibérica S.L.");
        ui.assertRowContains("suppliersTable", "Activo");
    }

    @Test
    @DisplayName("Mostrar inactivos añade el proveedor dado de baja")
    void showsInactiveSuppliers() {
        ui.setChecked("showInactiveCheck", true);

        ui.assertRowCount("suppliersTable", SeedFacts.SUPPLIERS - SeedFacts.ACTIVE_SUPPLIERS);
        ui.assertLabel("countLabel", "Proveedores: 1");
        ui.assertRowContains("suppliersTable", "Compusistemas Canarias S.L.");
        ui.assertRowContains("suppliersTable", "Inactivo");
    }

    @Test
    @DisplayName("Búsqueda por datos de contacto y limpieza de filtros")
    void searchesByContactData() {
        ui.type("searchField", "TechData");
        ui.assertRowCount("suppliersTable", 1);
        ui.assertRowContains("suppliersTable", "pedidos@techdata-iberica.es");

        ui.clear("searchField");
        ui.assertRowCount("suppliersTable", SeedFacts.ACTIVE_SUPPLIERS);
    }

    @Test
    @DisplayName("Alta de proveedor con validaciones de contacto y duplicados")
    void createsSupplierAndValidates() {
        ui.clickButton("Nuevo proveedor");
        ui.assertLabel("formTitleLabel", "Nuevo proveedor");
        ui.type("nameField", "QA Proveedor S.L.");
        ui.type("phoneField", "+34 600 111 222");
        ui.type("emailField", "qa@proveedor.test");
        ui.type("addressField", "Calle de Pruebas 1, Madrid");
        ui.clickButton("Guardar");

        ui.assertLabel("statusLabel", "Proveedor guardado correctamente.");
        ui.assertRowContains("suppliersTable", "QA Proveedor S.L.");
        assertEquals(1L, TestDatabase.longValue(
                "SELECT COUNT(*) FROM SUPPLIER WHERE name = 'QA Proveedor S.L.' AND active = 1"));

        ui.clickButton("Nuevo proveedor");
        ui.type("nameField", "TechData Iberica S.L.");
        ui.clickButton("Guardar");
        ui.assertLabel("errorLabel", "El proveedor 'TechData Iberica S.L.' ya existe.");

        ui.clickButton("Nuevo proveedor");
        ui.type("nameField", "QA Contacto Invalido");
        ui.type("emailField", "no-es-un-correo");
        ui.clickButton("Guardar");
        ui.assertLabel("errorLabel", "Introduce un correo electrónico válido.");
    }

    @Test
    @DisplayName("Baja y reactivación de un proveedor")
    void deactivatesAndReactivatesSupplier() {
        ui.selectRow("suppliersTable", "Papelería");
        ui.clickButton("Dar de baja proveedor");
        ui.confirmDialog();

        ui.assertLabel("statusLabel", "Proveedor 'Papelería y Consumibles Monte S.L.' dado de baja.");
        ui.assertRowCount("suppliersTable", 5);
        assertEquals(0L, TestDatabase.longValue(
                "SELECT active FROM SUPPLIER WHERE id = 6"));

        ui.setChecked("showInactiveCheck", true);
        ui.selectRow("suppliersTable", "Papelería");
        ui.clickButton("Reactivar proveedor");
        ui.assertLabel("statusLabel", "Proveedor 'Papelería y Consumibles Monte S.L.' reactivado.");
        assertEquals(1L, TestDatabase.longValue(
                "SELECT active FROM SUPPLIER WHERE id = 6"));
    }
}
