package com.entidaddesconocida.monte_erp.ui;

import com.entidaddesconocida.monte_erp.ui.support.FxRunner;
import com.entidaddesconocida.monte_erp.ui.support.SeedFacts;
import com.entidaddesconocida.monte_erp.ui.support.TempFiles;
import com.entidaddesconocida.monte_erp.ui.support.TestDatabase;
import com.entidaddesconocida.monte_erp.ui.support.UiActions;
import com.entidaddesconocida.monte_erp.ui.support.UiScreenshots;
import com.entidaddesconocida.monte_erp.ui.support.UiTestSupport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;

/**
 * Generates the screenshots of the user manual ({@code docs/manual_de_usuario}).
 *
 * <p>It is not part of the normal suites: it only runs when
 * {@code -Dmanual.capture=true} is passed (see {@code scripts/run-ui-tests.sh
 * --manual}), so the plain {@code -Pui-tests} run and {@code mvn test} skip it
 * without even starting Docker or the JavaFX toolkit.</p>
 *
 * <p>The walk-through is the one described in the manual: configuration,
 * sign-in, every module and the main operations (create, edit, deactivate,
 * receive stock, sell, export). Each screen is asserted with the same helpers
 * the UI tests use, so a screenshot never shows a screen that is not in the
 * expected state.</p>
 */
@Tag("ui")
@EnabledIfSystemProperty(named = "manual.capture", matches = "true",
        disabledReason = "Generador del manual: scripts/run-ui-tests.sh --manual")
class ManualCaptureUiTest {

    private static final String DEMO_URL = "jdbc:mysql://localhost:3306/monte_erp";

    private Stage stage;
    private UiTestSupport app;
    private UiActions ui;
    private UiScreenshots shots;

    @BeforeAll
    static void prepare() {
        TestDatabase.provision();
        FxRunner.ensureToolkitStarted();
    }

    @BeforeEach
    void openApplication() {
        TestDatabase.resetToSeed();
        stage = FxRunner.get(Stage::new);
        app = UiTestSupport.forSeedDatabase(stage);
        app.startAtLogin();
        ui = new UiActions(stage);
        ui.activateWindow();
        shots = new UiScreenshots(stage);
    }

    @AfterEach
    void closeApplication() {
        if (stage != null) {
            FxRunner.run(stage::close);
        }
    }

    @Test
    @DisplayName("Recorre la aplicación y guarda las capturas del manual")
    void capturesTheUserManual() throws IOException {
        configurationScreens();
        signInAndMainMenu();
        userManagement();
        categories();
        suppliers();
        customers();
        products();
        purchases();
        salesOrders();
        stock();
        reports();
        employeeMainMenu();

        for (Path file : shots.saved()) {
            System.out.println("Captura guardada: " + file);
        }
    }

    // --------------------------------------------------------------- screens

    /** Login, error message and both variants of the database setup screen. */
    private void configurationScreens() {
        ui.assertTitle("Monte ERP · Iniciar sesión");
        shots.capture("login");

        ui.type("usernameField", SeedFacts.ADMIN_USERNAME);
        ui.type("passwordField", "contraseña-incorrecta");
        ui.click("loginButton");
        ui.assertLabel("errorLabel", "Usuario o contraseña incorrectos.");
        shots.capture("login-error");
        ui.clear("usernameField");

        ui.clickButton("Cambiar configuración de base de datos");
        ui.assertTitle("Monte ERP · Configuración de la base de datos");
        ui.type("urlField", DEMO_URL);
        ui.assertVisible("adminSection", false);
        shots.capture("configuracion-conexion");
        ui.clickButton("Cancelar");
        ui.assertTitle("Monte ERP · Iniciar sesión");

        // First run: the same screen plus the administrator to be created.
        FxRunner.run(() -> app.appShell().showSetup(true));
        ui.assertTitle("Monte ERP · Configuración inicial");
        ui.type("urlField", DEMO_URL);
        ui.type("dbUserField", "erp_user");
        ui.type("dbPasswordField", "erp_pass");
        ui.assertVisible("adminSection", true);
        shots.capture("configuracion-inicial");

        FxRunner.run(() -> app.appShell().showSetup(false));
        ui.clickButton("Cancelar");
        ui.assertTitle("Monte ERP · Iniciar sesión");
    }

    private void signInAndMainMenu() {
        signIn(SeedFacts.ADMIN_USERNAME, SeedFacts.ADMIN_PASSWORD);
        ui.assertVisible("manageUsersButton", true);
        maskDatabaseEndpoint();
        shots.capture("menu-administrador");
    }

    private void userManagement() {
        ui.clickButton("Gestión de usuarios");
        ui.assertTitle("Monte ERP · Gestión de usuarios");
        ui.assertRowCount("usersTable", SeedFacts.USERS);
        shots.capture("usuarios");

        ui.clickButton("Nuevo usuario");
        ui.type("nameField", "María Ruiz");
        ui.type("usernameField", "maria.ruiz");
        ui.type("passwordField", "Demo1234");
        ui.selectCombo("roleCombo", "Empleado");
        ui.assertLabel("formTitleLabel", "Nuevo usuario");
        shots.capture("usuarios-nuevo");

        ui.clickButton("Guardar");
        ui.assertLabel("statusLabel", "Usuario guardado correctamente.");
        ui.assertRowContains("usersTable", "maria.ruiz | María Ruiz | Empleado | Activo");
        shots.capture("usuarios-guardado");

        ui.clickButton("Nuevo usuario");
        ui.type("nameField", "Usuario duplicado");
        ui.type("usernameField", SeedFacts.ADMIN_USERNAME);
        ui.type("passwordField", "Demo1234");
        ui.clickButton("Guardar");
        ui.assertLabel("errorLabel", "El nombre de usuario 'admin' ya existe.");
        shots.capture("usuarios-error");

        ui.selectRow("usersTable", "maria.ruiz");
        ui.clickButton("Desactivar usuario");
        ui.assertTrue("Debe pedirse confirmación", ui.dialogText().contains("maria.ruiz"));
        shots.captureDialog("dialogo-desactivar");
        ui.confirmDialog();
        ui.assertLabel("statusLabel", "Usuario 'maria.ruiz' desactivado.");
        ui.assertRowContains("usersTable", "maria.ruiz | María Ruiz | Empleado | Inactivo");
        shots.capture("usuarios-desactivado");

        ui.clickButton("Volver");
    }

    private void categories() {
        ui.clickButton("Categorías");
        ui.assertTitle("Monte ERP · Gestión de categorías");
        shots.capture("categorias");

        ui.clickButton("Nueva categoría");
        ui.type("nameField", "Accesorios de red");
        ui.type("descriptionField", "Latiguillos, rosetas y paneles de parcheo");
        shots.capture("categorias-nueva");

        ui.clickButton("Guardar");
        ui.assertLabel("statusLabel", "Categoría guardada correctamente.");
        ui.assertRowContains("categoriesTable", "Accesorios de red");
        shots.capture("categorias-guardada");

        ui.clickButton("Volver");
    }

    private void suppliers() {
        ui.clickButton("Proveedores");
        ui.assertTitle("Monte ERP · Gestión de proveedores");
        shots.capture("proveedores");

        ui.clickButton("Nuevo proveedor");
        ui.type("nameField", "Mayorista Ibérica S.L.");
        ui.type("phoneField", "+34 910 000 111");
        ui.type("emailField", "compras@mayorista-iberica.es");
        ui.type("addressField", "Calle de la Industria, 12, 28906 Getafe, Madrid");
        shots.capture("proveedores-nuevo");

        ui.clickButton("Guardar");
        ui.assertLabel("statusLabel", "Proveedor guardado correctamente.");
        shots.capture("proveedores-guardado");

        ui.clickButton("Volver");
    }

    private void customers() {
        ui.clickButton("Clientes");
        ui.assertTitle("Monte ERP · Gestión de clientes");
        shots.capture("clientes");

        ui.clickButton("Nuevo cliente");
        ui.type("nameField", "Colegio San Isidro");
        ui.type("phoneField", "+34 916 200 300");
        ui.type("emailField", "secretaria@colegiosanisidro.es");
        ui.type("addressField", "Calle de los Olivos, 8, 28904 Getafe, Madrid");
        shots.capture("clientes-nuevo");

        ui.clickButton("Guardar");
        ui.assertLabel("statusLabel", "Cliente guardado correctamente.");
        ui.assertRowContains("customersTable", "Colegio San Isidro");
        shots.capture("clientes-guardado");

        ui.clickButton("Volver");
    }

    private void products() {
        ui.clickButton("Productos");
        ui.assertTitle("Monte ERP · Gestión de productos");
        ui.assertRowCount("productsTable", SeedFacts.ACTIVE_PRODUCTS);
        shots.capture("productos");

        ui.type("searchField", "portatil");
        ui.assertRowCount("productsTable", 3);
        shots.capture("productos-filtro");
        ui.clear("searchField");

        ui.clickButton("Nuevo producto");
        ui.type("nameField", "Switch TP-Link TL-SG108");
        ui.type("skuField", "RED-SW-TL108");
        ui.type("descriptionField", "Switch Gigabit de 8 puertos para sobremesa");
        ui.type("priceField", "29.90");
        ui.type("minStockField", "4");
        ui.selectCombo("categoryCombo", "Redes");
        shots.capture("productos-nuevo");

        ui.clickButton("Guardar");
        ui.assertLabel("statusLabel", "Producto guardado correctamente.");
        ui.assertRowContains("productsTable", "RED-SW-TL108");
        shots.capture("productos-guardado");

        ui.selectRow("productsTable", "RED-SW-TL108");
        ui.clickButton("Dar de baja producto");
        ui.assertTrue("Debe pedirse confirmación",
                ui.dialogText().contains("Switch TP-Link TL-SG108"));
        shots.captureDialog("dialogo-baja-producto");
        ui.confirmDialog();
        ui.assertLabel("statusLabel", "Producto 'Switch TP-Link TL-SG108' dado de baja.");
        ui.assertNoRowContains("productsTable", "RED-SW-TL108");
        shots.capture("productos-dado-de-baja");

        ui.clickButton("Volver");
    }

    private void purchases() {
        ui.clickButton("Compras");
        ui.assertTitle("Monte ERP · Gestión de compras");
        ui.assertRowCount("purchasesTable", SeedFacts.PURCHASES);
        shots.capture("compras");

        ui.selectRow("purchasesTable", "#1");
        ui.assertTrue("La compra #1 debe tener lotes",
                ui.rowCount("purchaseLotsTable") > 0);
        shots.capture("compras-detalle");

        ui.clickButton("Nueva compra");
        ui.selectCombo("supplierCombo", "TechData");
        ui.selectCombo("productCombo", "COM-COR-RM750");
        ui.type("quantityField", "4");
        ui.type("purchasePriceField", "50.00");
        ui.clickButton("Añadir lote");
        ui.assertLabel("statusLabel", "Lote añadido.");
        ui.assertLabel("totalLabel", "Total: 200.00 €");
        shots.capture("compras-nueva");

        ui.clickButton("Guardar compra");
        ui.assertLabel("statusLabel", "Compra #19 guardada correctamente.");
        ui.assertRowContains("purchasesTable", "#19");
        shots.capture("compras-guardada");

        ui.clickTableAction("purchasesTable", "#19", "Recibir");
        ui.assertLabel("statusLabel", "Compra #19 marcada como Recibida.");
        shots.capture("compras-recibida");

        ui.clickButton("Volver");
    }

    private void salesOrders() {
        ui.clickButton("Pedidos");
        ui.assertTitle("Monte ERP · Gestión de pedidos");
        ui.assertRowCount("ordersTable", SeedFacts.SALES_ORDERS);
        shots.capture("pedidos");

        ui.selectRow("ordersTable", "#6");
        ui.assertRowCount("orderLinesTable", 4);
        ui.assertRowCount("consumedLotsTable", 4);
        shots.capture("pedidos-detalle");

        ui.clickButton("Nuevo pedido");
        ui.selectCombo("customerCombo", "Ayuntamiento de Getafe");
        ui.selectCombo("productCombo", "COM-COR-RM750");
        ui.type("quantityField", "2");
        ui.clickButton("Añadir línea");
        ui.assertLabel("statusLabel", "Línea añadida.");
        ui.assertLabel("totalLabel", "Total: 199.80 €");
        shots.capture("pedidos-nueva");

        ui.clickButton("Guardar pedido");
        ui.assertLabel("statusLabel", "Pedido #31 guardado correctamente.");
        ui.assertRowContains("ordersTable", "#31");
        shots.capture("pedidos-guardado");

        ui.clickTableAction("ordersTable", "#31", "Marcar enviado");
        ui.assertLabel("statusLabel", "Pedido #31 marcado como Enviado.");
        shots.capture("pedidos-enviado");

        ui.clickTableAction("ordersTable", "#31", "Marcar entregado");
        ui.assertLabel("statusLabel", "Pedido #31 marcado como Entregado.");
        shots.capture("pedidos-entregado");

        ui.clickButton("Volver");
    }

    private void stock() {
        ui.clickButton("Stock");
        ui.assertTitle("Monte ERP · Control de stock");
        ui.assertRowCount("levelsTable", SeedFacts.ACTIVE_PRODUCTS);
        shots.capture("stock");

        ui.setChecked("onlyBelowMinimumCheck", true);
        ui.assertRowCount("levelsTable", SeedFacts.STOCK_ALERTS);
        shots.capture("stock-alertas");

        ui.setChecked("onlyBelowMinimumCheck", false);
        ui.selectRow("levelsTable", "PORT-LEN-T14");
        ui.assertTrue("El producto debe tener lotes", ui.rowCount("lotsTable") > 0);
        shots.capture("stock-lotes");

        ui.clickButton("Volver");
    }

    private void reports() throws IOException {
        ui.clickButton("Informes");
        ui.assertTitle("Monte ERP · Informes");
        ui.assertTrue("El informe de stock debe tener los lotes del histórico",
                ui.rowCount("stockLotsTable") >= SeedFacts.STOCK_LOTS);
        shots.capture("informes-stock");

        ui.setDate("fromPicker", LocalDate.now().minusDays(30));
        ui.setDate("toPicker", LocalDate.now());
        ui.clickButton("Generar");
        ui.assertLabelEmpty("errorLabel");
        shots.capture("informes-periodo");

        ui.clickButton("Limpiar");
        ui.selectTab("reportsTabPane", 1);
        ui.assertTrue("El informe de ventas debe tener filas", ui.rowCount("salesTable") > 0);
        shots.capture("informes-ventas");

        app.fileSaveDialog().setNextFile(exportTarget("manual-informe-ventas", ".csv"));
        ui.clickButton("Exportar CSV");
        ui.assertTrue("Debe avisarse de la exportación",
                ui.dialogText().contains("Se ha guardado el informe en:"));
        shots.captureDialog("informes-exportacion");
        ui.confirmDialog();

        ui.clickButton("Volver");
    }

    private void employeeMainMenu() {
        ui.clickMenuItem("sessionMenuButton", "Cerrar sesión");
        ui.assertTitle("Monte ERP · Iniciar sesión");

        signIn(SeedFacts.EMPLOYEE_USERNAME, SeedFacts.EMPLOYEE_PASSWORD);
        ui.assertVisible("manageUsersButton", false);
        maskDatabaseEndpoint();
        shots.capture("menu-empleado");
    }

    // --------------------------------------------------------------- helpers

    private void signIn(String username, String password) {
        ui.type("usernameField", username);
        ui.type("passwordField", password);
        ui.click("loginButton");
        ui.assertLabel("usernameLabel", username);
    }

    /**
     * Shows the documented URL in the footer instead of the endpoint of the
     * throwaway container (a random port that means nothing to the reader).
     */
    private void maskDatabaseEndpoint() {
        FxRunner.run(() -> {
            if (stage.getScene() == null) {
                return;
            }
            Node node = stage.getScene().lookup("#databaseLabel");
            if (node instanceof Label label) {
                label.textProperty().unbind();
                label.setText("Base de datos: " + DEMO_URL);
            }
        });
    }

    /** File the fake "save as" dialog returns for the reports of the manual. */
    private static File exportTarget(String name, String extension) throws IOException {
        File file = File.createTempFile(name, extension, TempFiles.directory("exports").toFile());
        file.deleteOnExit();
        return file;
    }
}
