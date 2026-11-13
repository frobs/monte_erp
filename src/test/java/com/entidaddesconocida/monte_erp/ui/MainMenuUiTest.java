package com.entidaddesconocida.monte_erp.ui;

import com.entidaddesconocida.monte_erp.ui.support.AbstractUiTest;
import com.entidaddesconocida.monte_erp.ui.support.SeedFacts;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Main menu: modules per role, navigation and window actions.
 */
@Tag("ui")
class MainMenuUiTest extends AbstractUiTest {

    @Test
    @DisplayName("El administrador ve todos los módulos y cada uno abre su pantalla")
    void adminOpensEveryModule() {
        login(SeedFacts.ADMIN_USERNAME, SeedFacts.ADMIN_PASSWORD);
        ui.assertVisible("manageUsersButton", true);

        openAndReturn("Gestión de usuarios", "Monte ERP · Gestión de usuarios");
        openAndReturn("Productos", "Monte ERP · Gestión de productos");
        openAndReturn("Categorías", "Monte ERP · Gestión de categorías");
        openAndReturn("Proveedores", "Monte ERP · Gestión de proveedores");
        openAndReturn("Clientes", "Monte ERP · Gestión de clientes");
        openAndReturn("Compras", "Monte ERP · Gestión de compras");
        openAndReturn("Pedidos", "Monte ERP · Gestión de pedidos");
        openAndReturn("Stock", "Monte ERP · Control de stock");
        openAndReturn("Informes", "Monte ERP · Informes");
    }

    @Test
    @DisplayName("El empleado no tiene acceso a la gestión de usuarios")
    void employeeDoesNotSeeUserManagement() {
        login(SeedFacts.EMPLOYEE_USERNAME, SeedFacts.EMPLOYEE_PASSWORD);

        ui.assertVisible("manageUsersButton", false);
        ui.assertLabel("roleLabel", "Empleado");
    }

    @Test
    @DisplayName("ESC vuelve al menú desde un módulo")
    void escapeReturnsToTheMenu() {
        login(SeedFacts.ADMIN_USERNAME, SeedFacts.ADMIN_PASSWORD);
        ui.clickButton("Productos");
        ui.assertTitle("Monte ERP · Gestión de productos");

        ui.pressEscape();

        ui.assertTitle("Monte ERP · Administrador (Administrador)");
    }

    @Test
    @DisplayName("Salir cierra la ventana de la aplicación")
    void exitClosesTheWindow() {
        login(SeedFacts.ADMIN_USERNAME, SeedFacts.ADMIN_PASSWORD);

        ui.clickMenuItem("sessionMenuButton", "Salir");

        ui.assertTrue("La ventana sigue visible", !app.stage().isShowing());
    }

    private void login(String username, String password) {
        ui.type("usernameField", username);
        ui.type("passwordField", password);
        ui.click("loginButton");
        ui.assertLabel("usernameLabel", username);
    }

    private void openAndReturn(String menuButton, String expectedTitle) {
        ui.clickButton(menuButton);
        ui.assertTitle(expectedTitle);
        ui.clickButton("Volver");
        ui.assertTitleContains("Monte ERP · Administrador");
    }
}
