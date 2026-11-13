package com.entidaddesconocida.monte_erp.ui;

import com.entidaddesconocida.monte_erp.ui.support.AbstractUiTest;
import com.entidaddesconocida.monte_erp.ui.support.LogRecorder;
import com.entidaddesconocida.monte_erp.ui.support.SeedFacts;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * End-to-end walk through every screen and every role: the full business loop
 * (sign in, visit the modules, come back, sign out) without any unexpected
 * exception in the logs.
 */
@Tag("ui")
class NavigationUiTest extends AbstractUiTest {

    @Test
    @DisplayName("Recorrido completo del administrador sin errores en el log")
    void adminVisitsEveryModuleAndSignsOut() {
        try (LogRecorder logs = new LogRecorder()) {
            loginAsAdmin();

            for (String[] module : modulesForAdmin()) {
                ui.clickButton(module[0]);
                ui.assertTitle(module[1]);
                ui.clickButton("Volver");
                ui.assertTitleContains("Monte ERP · Administrador");
                ui.assertLabel("welcomeLabel", "Bienvenido/a, Administrador");
            }

            ui.clickMenuItem("sessionMenuButton", "Cerrar sesión");
            ui.assertTitle("Monte ERP · Iniciar sesión");

            logs.assertNoSevere();
        }
    }

    @Test
    @DisplayName("Recorrido completo del empleado sin errores en el log")
    void employeeVisitsEveryModuleAndSignsOut() {
        try (LogRecorder logs = new LogRecorder()) {
            loginAsEmployee();
            ui.assertVisible("manageUsersButton", false);

            for (String[] module : modulesForEmployee()) {
                ui.clickButton(module[0]);
                ui.assertTitle(module[1]);
                ui.clickButton("Volver");
                ui.assertTitleContains("Monte ERP · Ana García");
            }

            ui.clickMenuItem("sessionMenuButton", "Cerrar sesión");
            ui.assertTitle("Monte ERP · Iniciar sesión");

            logs.assertNoSevere();
        }
    }

    private String[][] modulesForAdmin() {
        return new String[][] {
                {"Gestión de usuarios", "Monte ERP · Gestión de usuarios"},
                {"Productos", "Monte ERP · Gestión de productos"},
                {"Categorías", "Monte ERP · Gestión de categorías"},
                {"Proveedores", "Monte ERP · Gestión de proveedores"},
                {"Clientes", "Monte ERP · Gestión de clientes"},
                {"Compras", "Monte ERP · Gestión de compras"},
                {"Pedidos", "Monte ERP · Gestión de pedidos"},
                {"Stock", "Monte ERP · Control de stock"},
                {"Informes", "Monte ERP · Informes"}
        };
    }

    private String[][] modulesForEmployee() {
        String[][] admin = modulesForAdmin();
        String[][] modules = new String[admin.length - 1][];
        System.arraycopy(admin, 1, modules, 0, modules.length);
        return modules;
    }
}
