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
 * CU-04 (RF-04): category management.
 */
@Tag("ui")
class CategoriesUiTest extends AbstractUiTest {

    @BeforeEach
    void openCategories() {
        loginAsAdmin();
        ui.clickButton("Categorías");
        ui.assertTitle("Monte ERP · Gestión de categorías");
    }

    @Test
    @DisplayName("La tabla muestra las categorías activas del seed")
    void listsActiveCategories() {
        ui.assertRowCount("categoriesTable", SeedFacts.ACTIVE_CATEGORIES);
        ui.assertLabel("countLabel", "Categorías: 6");
        ui.assertRowContains("categoriesTable", "Portátiles");
        ui.assertRowContains("categoriesTable", "Almacenamiento");
        ui.assertRowContains("categoriesTable", "Activa");
    }

    @Test
    @DisplayName("Mostrar inactivas añade las categorías dadas de baja")
    void showsInactiveCategories() {
        ui.setChecked("showInactiveCheck", true);

        ui.assertRowCount("categoriesTable", SeedFacts.CATEGORIES - SeedFacts.ACTIVE_CATEGORIES);
        ui.assertLabel("countLabel", "Categorías: 2");
        ui.assertRowContains("categoriesTable", "Impresión |");
        ui.assertRowContains("categoriesTable", "Sobremesa |");
        ui.assertRowContains("categoriesTable", "Inactiva");
    }

    @Test
    @DisplayName("La búsqueda ignora mayúsculas y acentos y se puede limpiar")
    void searchIsAccentInsensitive() {
        ui.type("searchField", "portatiles");

        ui.assertRowCount("categoriesTable", 1);
        ui.assertRowContains("categoriesTable", "Portátiles");

        ui.clear("searchField");
        ui.assertRowCount("categoriesTable", SeedFacts.ACTIVE_CATEGORIES);
    }

    @Test
    @DisplayName("Alta de categoría con validación de duplicados")
    void createsCategoryAndRejectsDuplicates() {
        ui.clickButton("Nueva categoría");
        ui.assertLabel("formTitleLabel", "Nueva categoría");
        ui.type("nameField", "QA Categoria");
        ui.type("descriptionField", "Categoría creada por los tests de interfaz");
        ui.clickButton("Guardar");

        ui.assertLabel("statusLabel", "Categoría guardada correctamente.");
        ui.assertLabel("countLabel", "Categorías: 7");
        ui.assertRowContains("categoriesTable", "QA Categoria");
        assertEquals(1L, TestDatabase.longValue(
                "SELECT COUNT(*) FROM CATEGORY WHERE name = 'QA Categoria' AND active = 1"));

        ui.clickButton("Nueva categoría");
        ui.type("nameField", "Portatiles");
        ui.clickButton("Guardar");
        ui.assertLabel("errorLabel", "La categoría 'Portatiles' ya existe.");
        ui.assertRowCount("categoriesTable", 7);
    }

    @Test
    @DisplayName("Baja y reactivación de una categoría")
    void deactivatesAndReactivatesCategory() {
        ui.selectRow("categoriesTable", "Redes");
        ui.clickButton("Dar de baja categoría");
        ui.confirmDialog();

        ui.assertLabel("statusLabel", "Categoría 'Redes' dada de baja.");
        ui.assertRowCount("categoriesTable", 5);
        assertEquals(0L, TestDatabase.longValue(
                "SELECT active FROM CATEGORY WHERE name = 'Redes'"));

        ui.setChecked("showInactiveCheck", true);
        ui.selectRow("categoriesTable", "Redes");
        ui.clickButton("Reactivar categoría");

        ui.assertLabel("statusLabel", "Categoría 'Redes' reactivada.");
        ui.setChecked("showInactiveCheck", false);
        ui.assertRowContains("categoriesTable", "Redes |");
        assertEquals(1L, TestDatabase.longValue(
                "SELECT active FROM CATEGORY WHERE name = 'Redes'"));
    }
}
