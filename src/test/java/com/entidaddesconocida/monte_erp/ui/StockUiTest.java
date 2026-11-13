package com.entidaddesconocida.monte_erp.ui;

import com.entidaddesconocida.monte_erp.ui.support.AbstractUiTest;
import com.entidaddesconocida.monte_erp.ui.support.SeedFacts;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * CU-08 (RF-09 / RF-10): stock levels, alarms and lot detail.
 */
@Tag("ui")
class StockUiTest extends AbstractUiTest {

    @BeforeEach
    void openStock() {
        loginAsAdmin();
        ui.clickButton("Stock");
        ui.assertTitle("Monte ERP · Control de stock");
    }

    @Test
    @DisplayName("Niveles de stock y alertas del seed")
    void listsLevelsAndAlarms() {
        ui.assertRowCount("levelsTable", SeedFacts.ACTIVE_PRODUCTS);
        ui.assertLabel("countLabel", "23 productos");
        ui.assertLabel("alertCountLabel", "Productos bajo mínimo: 9");
        ui.assertRowContains("levelsTable", "PORT-LEN-T14");
        ui.assertRowContains("levelsTable", "Bajo mínimo");
        ui.assertRowContains("levelsTable", "OK");
    }

    @Test
    @DisplayName("Solo bajo mínimo deja las nueve alertas")
    void filtersOnlyBelowMinimum() {
        ui.setChecked("onlyBelowMinimumCheck", true);

        ui.assertRowCount("levelsTable", SeedFacts.STOCK_ALERTS);
        ui.assertLabel("countLabel", "9 productos");
        ui.assertRowContains("levelsTable", "Placa base MSI B650 Tomahawk");
        ui.assertRowContains("levelsTable", "Procesador AMD Ryzen 7 7700X");

        ui.setChecked("onlyBelowMinimumCheck", false);
        ui.assertRowCount("levelsTable", SeedFacts.ACTIVE_PRODUCTS);
    }

    @Test
    @DisplayName("Búsqueda por nombre o SKU")
    void searchesByNameOrSku() {
        ui.type("searchField", "portatil");

        ui.assertRowCount("levelsTable", 3);
        ui.assertRowContains("levelsTable", "PORT-LEN-T14");
        ui.assertRowContains("levelsTable", "PORT-HP-840");
        ui.assertRowContains("levelsTable", "PORT-APP-MBA");

        ui.clear("searchField");
        ui.assertRowCount("levelsTable", SeedFacts.ACTIVE_PRODUCTS);
    }

    @Test
    @DisplayName("Seleccionar un producto muestra sus lotes, incluidos los no disponibles")
    void showsProductLots() {
        ui.selectRow("levelsTable", "PORT-LEN-T14");

        ui.assertRowCount("lotsTable", 3);
        ui.assertRowContains("lotsTable", "#1");
        ui.assertRowContains("lotsTable", "#8");
        ui.assertRowContains("lotsTable", "#15");
    }
}
