package com.entidaddesconocida.monte_erp.viewmodel;

import com.entidaddesconocida.monte_erp.domain.model.Category;
import com.entidaddesconocida.monte_erp.viewmodel.support.FakeCategoryRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link CategoriesViewModel} (RF-04 / CU-04).
 *
 * <p>No database and no JavaFX toolkit: the ViewModel is built by hand with an
 * in-memory repository. The per-field validation properties replace ValidatorFX
 * here, so the rules are asserted without a toolkit.</p>
 */
class CategoriesViewModelTest {

    private static final Category DRINKS = new Category(1, "Bebidas", "Refrescos");

    private FakeCategoryRepository fakeRepository;
    private CategoriesViewModel viewModel;

    @BeforeEach
    void setUp() {
        fakeRepository = new FakeCategoryRepository().withCategory(DRINKS);
        viewModel = new CategoriesViewModel(fakeRepository);
        viewModel.load();
    }

    private void fillValidForm() {
        viewModel.nameProperty().set("Snacks");
        viewModel.descriptionProperty().set("Aperitivos");
    }

    @Test
    @DisplayName("load carga las categorías y el contador")
    void loadFillsTheListAndCount() {
        assertEquals(1, viewModel.categories().size());
        assertEquals("Categorías: 1", viewModel.categoryCountProperty().get());
    }

    @Test
    @DisplayName("Alta válida: inserta la categoría activa con su descripción")
    void createValidCategory() throws Exception {
        fillValidForm();

        viewModel.save();

        assertEquals("", viewModel.errorMessageProperty().get());
        assertTrue(viewModel.statusMessageProperty().get().contains("guardada"));
        Category created = fakeRepository.findByName("Snacks");
        assertEquals("Aperitivos", created.getDescription());
        assertTrue(created.isActive());
    }

    @Test
    @DisplayName("La descripción vacía se guarda como nula")
    void blankDescriptionIsSavedAsNull() throws Exception {
        viewModel.nameProperty().set("Snacks");

        viewModel.save();

        assertNull(fakeRepository.findByName("Snacks").getDescription());
    }

    @Test
    @DisplayName("Nombre vacío: error de campo y no se toca la base de datos")
    void emptyNameShowsFieldError() throws Exception {
        viewModel.descriptionProperty().set("Aperitivos");

        viewModel.save();

        assertEquals("Introduce el nombre de la categoría.", viewModel.nameErrorProperty().get());
        assertEquals(1, fakeRepository.findAll().size());
    }

    @Test
    @DisplayName("Nombre de más de 100 caracteres: error de campo")
    void nameTooLongShowsFieldError() {
        viewModel.nameProperty().set("a".repeat(101));

        viewModel.save();

        assertEquals("El nombre no puede superar los 100 caracteres.",
                viewModel.nameErrorProperty().get());
    }

    @Test
    @DisplayName("Descripción de más de 255 caracteres: error de campo")
    void descriptionTooLongShowsFieldError() {
        viewModel.nameProperty().set("Snacks");
        viewModel.descriptionProperty().set("a".repeat(256));

        viewModel.save();

        assertEquals("La descripción no puede superar los 255 caracteres.",
                viewModel.descriptionErrorProperty().get());
    }

    @Test
    @DisplayName("Nombre duplicado: error de campo y no se inserta")
    void duplicatedNameShowsFieldError() throws Exception {
        viewModel.nameProperty().set("Bebidas");

        viewModel.save();

        assertEquals("La categoría 'Bebidas' ya existe.", viewModel.nameErrorProperty().get());
        assertEquals(1, fakeRepository.findAll().size());
    }

    @Test
    @DisplayName("Nombre duplicado con otra capitalización: se rechaza")
    void duplicatedNameIsCaseInsensitive() {
        viewModel.nameProperty().set("bebidas");

        viewModel.save();

        assertEquals("La categoría 'bebidas' ya existe.", viewModel.nameErrorProperty().get());
    }

    @Test
    @DisplayName("El nombre de una categoría dada de baja sigue reservado: se rechaza el duplicado")
    void duplicatedNameOfInactiveCategoryIsRejected() throws Exception {
        // UNIQUE(name) is global, so a deactivated category still owns its name
        // and has to be reactivated instead of duplicated.
        fakeRepository = new FakeCategoryRepository()
                .withCategory(new Category(7, "Bebidas", "Refrescos", false));
        viewModel = new CategoriesViewModel(fakeRepository);
        viewModel.load();
        viewModel.nameProperty().set("Bebidas");

        viewModel.save();

        assertEquals("La categoría 'Bebidas' ya existe.", viewModel.nameErrorProperty().get());
        assertEquals(1, fakeRepository.findAll().size());
    }

    @Test
    @DisplayName("El contador refleja las filas filtradas por la búsqueda")
    void countFollowsTheSearchFilter() {
        fakeRepository.withCategory(new Category(2, "Snacks", "Aperitivos"));
        viewModel.load();
        assertEquals("Categorías: 2", viewModel.categoryCountProperty().get());

        viewModel.searchTextProperty().set("snack");

        assertEquals(1, viewModel.categories().size());
        assertEquals("Categorías: 1", viewModel.categoryCountProperty().get());
    }

    @Test
    @DisplayName("Un duplicado detectado por la clave única al insertar se reporta como duplicado")
    void integrityViolationOnInsertIsReportedAsDuplicate() {
        FakeCategoryRepository racing = new FakeCategoryRepository() {
            @Override
            public Category findByName(String name) {
                return null;
            }

            @Override
            public int insert(Category category) throws SQLException {
                throw new SQLIntegrityConstraintViolationException(
                        "Duplicate entry for key 'uq_category_name'");
            }
        };
        CategoriesViewModel racingViewModel = new CategoriesViewModel(racing);
        racingViewModel.nameProperty().set("Bebidas");

        racingViewModel.save();

        assertEquals("La categoría 'Bebidas' ya existe.", racingViewModel.nameErrorProperty().get());
    }

    @Test
    @DisplayName("Editar conserva id y estado de la categoría")
    void editPreservesIdAndStatus() throws Exception {
        Category retired = new Category(2, "Antigua", "Ya no se usa", false);
        fakeRepository.withCategory(retired);
        viewModel.showInactiveProperty().set(true);
        viewModel.load();
        viewModel.selectedCategoryProperty().set(viewModel.categories().get(0));

        viewModel.nameProperty().set("Renombrada");
        viewModel.save();

        Category updated = fakeRepository.findByName("Renombrada");
        assertEquals(2, updated.getId());
        assertFalse(updated.isActive());
    }

    @Test
    @DisplayName("Editar a un nombre ya existente: error de duplicado")
    void editToExistingNameFails() throws Exception {
        Category snacks = new Category(2, "Snacks", "Aperitivos");
        fakeRepository.withCategory(snacks);
        viewModel.load();
        viewModel.selectedCategoryProperty().set(viewModel.categories().stream()
                .filter(category -> category.getName().equals("Snacks"))
                .findFirst().orElseThrow());

        viewModel.nameProperty().set("Bebidas");
        viewModel.save();

        assertEquals("La categoría 'Bebidas' ya existe.", viewModel.nameErrorProperty().get());
    }

    @Test
    @DisplayName("Dar de baja: la categoría queda inactiva (baja lógica)")
    void deactivateMakesCategoryInactive() throws Exception {
        viewModel.selectedCategoryProperty().set(viewModel.categories().get(0));

        viewModel.deactivate();

        assertFalse(fakeRepository.findByName("Bebidas").isActive());
        assertTrue(viewModel.statusMessageProperty().get().contains("baja"));
        assertEquals("", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("Reactivar: una categoría inactiva vuelve a estar activa")
    void activateMakesCategoryActiveAgain() throws Exception {
        Category retired = new Category(2, "Antigua", null, false);
        fakeRepository.withCategory(retired);
        viewModel.showInactiveProperty().set(true);
        viewModel.load();
        viewModel.selectedCategoryProperty().set(viewModel.categories().get(0));

        viewModel.activate();

        assertTrue(fakeRepository.findByName("Antigua").isActive());
        assertTrue(viewModel.statusMessageProperty().get().contains("reactivada"));
    }

    @Test
    @DisplayName("Dar de baja sin selección muestra error")
    void deactivateWithoutSelectionFails() {
        viewModel.selectedCategoryProperty().set(null);

        viewModel.deactivate();

        assertEquals("Selecciona una categoría de la tabla.",
                viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("Dar de baja una categoría ya inactiva avisa sin tocar la base de datos")
    void deactivatingAnInactiveCategoryFails() throws Exception {
        Category retired = new Category(2, "Antigua", null, false);
        fakeRepository.withCategory(retired);
        viewModel.showInactiveProperty().set(true);
        viewModel.load();
        viewModel.selectedCategoryProperty().set(viewModel.categories().get(0));

        viewModel.deactivate();

        assertEquals("La categoría ya está dada de baja.", viewModel.errorMessageProperty().get());
        assertFalse(fakeRepository.findByName("Antigua").isActive());
    }

    @Test
    @DisplayName("Reactivar una categoría ya activa avisa sin tocar la base de datos")
    void activatingAnActiveCategoryFails() throws Exception {
        viewModel.selectedCategoryProperty().set(viewModel.categories().get(0));

        viewModel.activate();

        assertEquals("La categoría ya está activa.", viewModel.errorMessageProperty().get());
        assertTrue(fakeRepository.findByName("Bebidas").isActive());
    }

    @Test
    @DisplayName("Mostrar inactivas recarga la lista con las categorías dadas de baja")
    void showInactiveReloadsTheList() throws Exception {
        fakeRepository.withCategory(new Category(2, "Antigua", null, false));
        viewModel.load();
        assertEquals(1, viewModel.categories().size());

        viewModel.showInactiveProperty().set(true);

        assertEquals(1, viewModel.categories().size());
        assertEquals("Antigua", viewModel.categories().get(0).getName());

        viewModel.showInactiveProperty().set(false);
        assertEquals("Bebidas", viewModel.categories().get(0).getName());
    }

    @Test
    @DisplayName("La búsqueda por nombre acota la tabla")
    void searchFiltersByName() throws Exception {
        fakeRepository.withCategory(new Category(2, "Snacks", "Aperitivos"));
        viewModel.load();

        viewModel.searchTextProperty().set("sna");

        assertEquals(1, viewModel.categories().size());
        assertEquals("Snacks", viewModel.categories().get(0).getName());

        viewModel.searchTextProperty().set("");
        assertEquals(2, viewModel.categories().size());
    }

    @Test
    @DisplayName("La búsqueda por descripción acota la tabla")
    void searchFiltersByDescription() {
        fakeRepository.withCategory(new Category(2, "Snacks", "Aperitivos salados"));
        viewModel.load();

        viewModel.searchTextProperty().set("aperitivos");

        assertEquals(1, viewModel.categories().size());
        assertEquals("Snacks", viewModel.categories().get(0).getName());

        // La búsqueda ignora mayúsculas y acentos, igual que MySQL (utf8mb4_unicode_ci).
        viewModel.searchTextProperty().set("REFRÉSCOS");
        assertEquals(1, viewModel.categories().size());
        assertEquals("Bebidas", viewModel.categories().get(0).getName());
    }

    @Test
    @DisplayName("Error de base de datos al cargar: muestra mensaje de conexión")
    void databaseErrorOnLoadShowsMessage() {
        fakeRepository.throwOnAccess();

        viewModel.load();

        assertTrue(viewModel.errorMessageProperty().get().contains("No se pudo conectar"));
    }

    @Test
    @DisplayName("Error de base de datos al guardar: muestra mensaje de conexión")
    void databaseErrorOnSaveShowsMessage() {
        fillValidForm();
        fakeRepository.throwOnAccess();

        viewModel.save();

        assertTrue(viewModel.errorMessageProperty().get().contains("No se pudo conectar"));
    }

    @Test
    @DisplayName("Si la recarga falla tras guardar, la escritura sigue persistida y se avisa aparte")
    void failedReloadAfterSaveStillReportsTheWrite() throws Exception {
        fillValidForm();
        fakeRepository.failOn("findActive");

        viewModel.save();

        assertEquals("Snacks", fakeRepository.findByName("Snacks").getName());
        assertTrue(viewModel.errorMessageProperty().get().contains("se guardaron"));
    }

    @Test
    @DisplayName("La validación en vivo solo actúa tras el primer intento de guardado")
    void liveValidationStartsAfterFirstSaveAttempt() {
        viewModel.nameProperty().set("");
        assertTrue(viewModel.nameErrorProperty().get().isEmpty());

        viewModel.save();
        assertEquals("Introduce el nombre de la categoría.", viewModel.nameErrorProperty().get());

        viewModel.nameProperty().set("Snacks");
        assertTrue(viewModel.nameErrorProperty().get().isEmpty());
    }

    @Test
    @DisplayName("Nueva categoría limpia el formulario y los errores de campo")
    void newCategoryResetsTheForm() throws Exception {
        viewModel.selectedCategoryProperty().set(viewModel.categories().get(0));
        assertEquals("Editar categoría", viewModel.formTitleProperty().get());

        viewModel.save();
        viewModel.newCategory();

        assertNull(viewModel.selectedCategoryProperty().get());
        assertEquals("Nueva categoría", viewModel.formTitleProperty().get());
        assertEquals("", viewModel.nameProperty().get());
        assertEquals("", viewModel.descriptionProperty().get());
        assertTrue(viewModel.nameErrorProperty().get().isEmpty());
    }

    @Test
    @DisplayName("Seleccionar una categoría carga sus datos en el formulario")
    void selectingACategoryFillsTheForm() {
        viewModel.selectedCategoryProperty().set(viewModel.categories().get(0));

        assertEquals("Bebidas", viewModel.nameProperty().get());
        assertEquals("Refrescos", viewModel.descriptionProperty().get());
        assertEquals("Editar categoría", viewModel.formTitleProperty().get());
    }

    @Test
    @DisplayName("El estado del botón dar de baja/reactivar depende de la categoría seleccionada")
    void toggleStateFollowsSelection() throws Exception {
        fakeRepository.withCategory(new Category(2, "Antigua", null, false));
        viewModel.load();

        assertTrue(viewModel.toggleDisabledProperty().get());
        assertEquals(CategoriesViewModel.TOGGLE_DEACTIVATE_LABEL,
                viewModel.toggleLabelProperty().get());

        viewModel.selectedCategoryProperty().set(viewModel.categories().get(0));
        assertFalse(viewModel.toggleDisabledProperty().get());
        assertEquals(CategoriesViewModel.TOGGLE_DEACTIVATE_LABEL,
                viewModel.toggleLabelProperty().get());

        // Switching to the inactive list drops the (active) selection; selecting
        // the inactive category offers reactivation.
        viewModel.showInactiveProperty().set(true);
        assertTrue(viewModel.toggleDisabledProperty().get());
        viewModel.selectedCategoryProperty().set(viewModel.categories().get(0));
        assertEquals(CategoriesViewModel.TOGGLE_ACTIVATE_LABEL,
                viewModel.toggleLabelProperty().get());
        assertFalse(viewModel.toggleDisabledProperty().get());
    }
}
