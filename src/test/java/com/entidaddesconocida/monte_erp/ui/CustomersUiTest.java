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
 * CU-06 (RF-06): customer management.
 */
@Tag("ui")
class CustomersUiTest extends AbstractUiTest {

    @BeforeEach
    void openCustomers() {
        loginAsAdmin();
        ui.clickButton("Clientes");
        ui.assertTitle("Monte ERP · Gestión de clientes");
    }

    @Test
    @DisplayName("La tabla muestra los clientes activos del seed")
    void listsActiveCustomers() {
        ui.assertRowCount("customersTable", SeedFacts.ACTIVE_CUSTOMERS);
        ui.assertLabel("countLabel", "Clientes: 10");
        ui.assertRowContains("customersTable", "Ayuntamiento de Getafe");
        ui.assertRowContains("customersTable", "Activo");
    }

    @Test
    @DisplayName("Mostrar inactivos añade los clientes dados de baja")
    void showsInactiveCustomers() {
        ui.setChecked("showInactiveCheck", true);

        ui.assertRowCount("customersTable", SeedFacts.CUSTOMERS - SeedFacts.ACTIVE_CUSTOMERS);
        ui.assertLabel("countLabel", "Clientes: 2");
        ui.assertRowContains("customersTable", "Coffee & Code S.L.");
        ui.assertRowContains("customersTable", "Universidad Rural de Valladolid");
        ui.assertRowContains("customersTable", "Inactivo");
    }

    @Test
    @DisplayName("Búsqueda por nombre y limpieza de filtros")
    void searchesByName() {
        ui.type("searchField", "Ayuntamiento");

        ui.assertRowCount("customersTable", 1);
        ui.assertRowContains("customersTable", "compras@ayto-getafe.es");

        ui.clear("searchField");
        ui.assertRowCount("customersTable", SeedFacts.ACTIVE_CUSTOMERS);
    }

    @Test
    @DisplayName("Alta de cliente con validaciones de contacto")
    void createsCustomerAndValidates() {
        ui.clickButton("Nuevo cliente");
        ui.assertLabel("formTitleLabel", "Nuevo cliente");
        ui.type("nameField", "QA Cliente S.L.");
        ui.type("phoneField", "+34 600 333 444");
        ui.type("emailField", "qa@cliente.test");
        ui.type("addressField", "Avenida de Pruebas 2, Valencia");
        ui.clickButton("Guardar");

        ui.assertLabel("statusLabel", "Cliente guardado correctamente.");
        ui.assertRowContains("customersTable", "QA Cliente S.L.");
        assertEquals(1L, TestDatabase.longValue(
                "SELECT COUNT(*) FROM CUSTOMER WHERE name = 'QA Cliente S.L.' AND active = 1"));

        ui.clickButton("Nuevo cliente");
        ui.type("nameField", "QA Cliente Invalido");
        ui.type("phoneField", "telefono-invalido");
        ui.clickButton("Guardar");
        ui.assertLabel("errorLabel",
                "El teléfono solo puede contener números y los signos +, -, ( ), punto o espacios.");

        ui.type("phoneField", "+34 600 555 666");
        ui.type("emailField", "sin-arroba");
        ui.clickButton("Guardar");
        ui.assertLabel("errorLabel", "Introduce un correo electrónico válido.");
    }

    @Test
    @DisplayName("Un cliente con pedidos se puede dar de baja y reactivar")
    void deactivatesAndReactivatesCustomer() {
        ui.selectRow("customersTable", "Hotel Costa Azul");
        ui.clickButton("Dar de baja cliente");
        ui.confirmDialog();

        ui.assertLabel("statusLabel", "Cliente 'Hotel Costa Azul' dado de baja.");
        assertEquals(0L, TestDatabase.longValue(
                "SELECT active FROM CUSTOMER WHERE name = 'Hotel Costa Azul'"));

        ui.setChecked("showInactiveCheck", true);
        ui.selectRow("customersTable", "Hotel Costa Azul");
        ui.clickButton("Reactivar cliente");

        ui.assertLabel("statusLabel", "Cliente 'Hotel Costa Azul' reactivado.");
        assertEquals(1L, TestDatabase.longValue(
                "SELECT active FROM CUSTOMER WHERE name = 'Hotel Costa Azul'"));
    }
}
