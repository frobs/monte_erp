package com.entidaddesconocida.monte_erp.ui;

import com.entidaddesconocida.monte_erp.ui.support.AbstractUiTest;
import com.entidaddesconocida.monte_erp.ui.support.SeedFacts;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * CU-01: sign in, sign out and the entry point of the application.
 */
@Tag("ui")
class LoginUiTest extends AbstractUiTest {

    @Test
    @DisplayName("El administrador entra y ve el menú completo")
    void adminSignsInAndSeesEveryModule() {
        login(SeedFacts.ADMIN_USERNAME, SeedFacts.ADMIN_PASSWORD);

        ui.assertTitle("Monte ERP · Administrador (Administrador)");
        ui.assertLabel("welcomeLabel", "Bienvenido/a, Administrador");
        ui.assertLabel("usernameLabel", "admin");
        ui.assertLabel("roleLabel", "Administrador");
        ui.assertLabelContains("databaseLabel", "jdbc:mysql://");
        ui.assertVisible("manageUsersButton", true);
        assertEquals("admin", app.session().currentUser().getUsername());
    }

    @Test
    @DisplayName("El empleado entra y no puede gestionar usuarios")
    void employeeSignsInWithoutUserManagement() {
        login(SeedFacts.EMPLOYEE_USERNAME, SeedFacts.EMPLOYEE_PASSWORD);

        ui.assertTitle("Monte ERP · Ana García (Empleado)");
        ui.assertLabel("usernameLabel", "ana.garcia");
        ui.assertLabel("roleLabel", "Empleado");
        ui.assertVisible("manageUsersButton", false);
        assertNotNull(app.session().currentUser());
    }

    @Test
    @DisplayName("Contraseña incorrecta: mensaje genérico y contraseña borrada")
    void wrongPasswordShowsGenericError() {
        login(SeedFacts.ADMIN_USERNAME, "contraseña-incorrecta");

        ui.assertLabel("errorLabel", "Usuario o contraseña incorrectos.");
        ui.assertLabelEmpty("passwordField");
        assertNull(app.session().currentUser());
    }

    @Test
    @DisplayName("Usuario inactivo: no se revela el estado de la cuenta")
    void inactiveUserIsRejectedWithTheSameMessage() {
        login(SeedFacts.INACTIVE_USERNAME, SeedFacts.EMPLOYEE_PASSWORD);

        ui.assertLabel("errorLabel", "Usuario o contraseña incorrectos.");
        assertNull(app.session().currentUser());
    }

    @Test
    @DisplayName("Campos vacíos: se pide usuario y contraseña")
    void emptyFieldsAreRejected() {
        ui.click("loginButton");

        ui.assertLabel("errorLabel", "Introduce usuario y contraseña.");
        assertNull(app.session().currentUser());
    }

    @Test
    @DisplayName("Intro en la contraseña inicia sesión")
    void enterKeySignsIn() {
        ui.type("usernameField", SeedFacts.EMPLOYEE_USERNAME);
        ui.type("passwordField", SeedFacts.EMPLOYEE_PASSWORD);
        ui.pressEnter();

        ui.assertTitleContains("Monte ERP · Ana García");
    }

    @Test
    @DisplayName("Cerrar sesión vuelve al login y limpia la sesión")
    void signOutReturnsToLogin() {
        login(SeedFacts.ADMIN_USERNAME, SeedFacts.ADMIN_PASSWORD);
        ui.assertTitleContains("Administrador");

        ui.clickMenuItem("sessionMenuButton", "Cerrar sesión");

        ui.assertTitle("Monte ERP · Iniciar sesión");
        assertNull(app.session().currentUser());
    }

    @Test
    @DisplayName("Cambiar configuración abre el formulario de conexión sin sección de administrador")
    void changeDatabaseConfigurationOpensSetup() {
        ui.clickButton("Cambiar configuración de base de datos");

        ui.assertTitle("Monte ERP · Configuración de la base de datos");
        ui.assertVisible("adminSection", false);
        ui.assertLabelContains("urlField", "");
        ui.assertTrue("La URL debe estar precargada",
                ui.textOf("urlField").startsWith("jdbc:mysql://"));
    }

    private void login(String username, String password) {
        ui.type("usernameField", username);
        ui.type("passwordField", password);
        ui.click("loginButton");
    }
}
