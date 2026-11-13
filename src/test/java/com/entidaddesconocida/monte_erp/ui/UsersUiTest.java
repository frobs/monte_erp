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
 * CU-02 (RF-02): user management, exclusive to the administrator.
 */
@Tag("ui")
class UsersUiTest extends AbstractUiTest {

    @BeforeEach
    void openUserManagement() {
        loginAsAdmin();
        ui.clickButton("Gestión de usuarios");
        ui.assertTitle("Monte ERP · Gestión de usuarios");
    }

    @Test
    @DisplayName("La tabla muestra los usuarios del seed")
    void listsSeedUsers() {
        ui.assertRowCount("usersTable", SeedFacts.USERS);
        ui.assertLabel("countLabel", "Usuarios registrados: 4");
        ui.assertRowContains("usersTable", "admin | Administrador | Administrador | Activo");
        ui.assertRowContains("usersTable", "ana.garcia | Ana García | Empleado | Activo");
        ui.assertRowContains("usersTable", "marta.sanz | Marta Sanz | Empleado | Inactivo");
    }

    @Test
    @DisplayName("Alta de un empleado: formulario, tabla y base de datos")
    void createsEmployee() {
        ui.clickButton("Nuevo usuario");
        ui.assertLabel("formTitleLabel", "Nuevo usuario");
        ui.type("nameField", "QA Empleado");
        ui.type("usernameField", "qa.empleado");
        ui.type("passwordField", "Demo1234");
        ui.clickButton("Guardar");

        ui.assertLabel("statusLabel", "Usuario guardado correctamente.");
        ui.assertLabel("countLabel", "Usuarios registrados: 5");
        ui.assertRowContains("usersTable", "qa.empleado | QA Empleado | Empleado | Activo");
        assertEquals(1L, TestDatabase.longValue(
                "SELECT COUNT(*) FROM USER WHERE username = 'qa.empleado' AND active = 1"));
    }

    @Test
    @DisplayName("Usuario duplicado: se rechaza sin crear una fila")
    void rejectsDuplicateUsername() {
        ui.clickButton("Nuevo usuario");
        ui.type("nameField", "Duplicado");
        ui.type("usernameField", "admin");
        ui.type("passwordField", "Demo1234");
        ui.clickButton("Guardar");

        ui.assertLabel("errorLabel", "El nombre de usuario 'admin' ya existe.");
        ui.assertRowCount("usersTable", SeedFacts.USERS);
    }

    @Test
    @DisplayName("Campos obligatorios: nombre, usuario y contraseña")
    void rejectsEmptyFields() {
        ui.clickButton("Nuevo usuario");
        ui.clickButton("Guardar");
        ui.assertLabel("errorLabel", "Introduce el nombre del usuario.");

        ui.type("nameField", "Sin usuario");
        ui.clickButton("Guardar");
        ui.assertLabel("errorLabel", "Introduce el nombre de usuario.");

        ui.type("usernameField", "sin.password");
        ui.clickButton("Guardar");
        ui.assertLabel("errorLabel", "Introduce la contraseña del usuario.");
        ui.assertRowCount("usersTable", SeedFacts.USERS);
    }

    @Test
    @DisplayName("Editar sin contraseña conserva la contraseña actual")
    void editKeepsTheCurrentPassword() {
        String hashBefore = TestDatabase.stringValue(
                "SELECT password_hash FROM USER WHERE username = 'ana.garcia'");
        ui.selectRow("usersTable", "ana.garcia");
        ui.assertLabel("formTitleLabel", "Editar usuario");

        ui.type("nameField", "Ana García Editada");
        ui.clickButton("Guardar");

        ui.assertRowContains("usersTable", "ana.garcia | Ana García Editada");
        assertEquals(hashBefore, TestDatabase.stringValue(
                "SELECT password_hash FROM USER WHERE username = 'ana.garcia'"));
    }

    @Test
    @DisplayName("El administrador no puede desactivarse ni quitarse el rol")
    void adminCannotBreakTheirOwnAccount() {
        ui.selectRow("usersTable", "admin");
        ui.assertDisabled("toggleActiveButton", true);

        ui.selectCombo("roleCombo", "Empleado");
        ui.clickButton("Guardar");

        ui.assertLabel("errorLabel", "No puedes quitarte el rol de administrador.");
        ui.assertRowContains("usersTable", "admin | Administrador | Administrador | Activo");
    }

    @Test
    @DisplayName("Baja y reactivación lógica de un empleado")
    void deactivatesAndReactivatesEmployee() {
        ui.selectRow("usersTable", "ana.garcia");
        ui.clickButton("Desactivar usuario");
        ui.confirmDialog();

        ui.assertLabel("statusLabel", "Usuario 'ana.garcia' desactivado.");
        ui.assertRowContains("usersTable", "ana.garcia | Ana García | Empleado | Inactivo");
        assertEquals(0L, TestDatabase.longValue(
                "SELECT active FROM USER WHERE username = 'ana.garcia'"));

        ui.selectRow("usersTable", "ana.garcia");
        ui.clickButton("Activar usuario");

        ui.assertRowContains("usersTable", "ana.garcia | Ana García | Empleado | Activo");
        assertEquals(1L, TestDatabase.longValue(
                "SELECT active FROM USER WHERE username = 'ana.garcia'"));
        assertEquals(1L, TestDatabase.longValue(
                "SELECT COUNT(*) FROM USER WHERE username = 'ana.garcia' AND active = 1"));
    }
}
