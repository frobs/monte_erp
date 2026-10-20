package com.entidaddesconocida.monte_erp.viewmodel;

import com.entidaddesconocida.monte_erp.domain.model.Supplier;
import com.entidaddesconocida.monte_erp.viewmodel.support.FakeSupplierRepository;

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
 * Unit tests for {@link SuppliersViewModel} (RF-05 / CU-05).
 *
 * <p>No database and no JavaFX toolkit: the ViewModel is built by hand with an
 * in-memory repository. The per-field validation properties replace ValidatorFX
 * here, so the rules are asserted without a toolkit.</p>
 */
class SuppliersViewModelTest {

    private static final Supplier ACME = new Supplier(1, "Acme", "600111222", "acme@example.com");

    private FakeSupplierRepository fakeRepository;
    private SuppliersViewModel viewModel;

    @BeforeEach
    void setUp() {
        fakeRepository = new FakeSupplierRepository().withSupplier(ACME);
        viewModel = new SuppliersViewModel(fakeRepository);
        viewModel.load();
    }

    private void fillValidForm() {
        viewModel.nameProperty().set("Globex");
        viewModel.phoneProperty().set("600333444");
        viewModel.emailProperty().set("globex@example.com");
    }

    @Test
    @DisplayName("load carga los proveedores y el contador")
    void loadFillsTheListAndCount() {
        assertEquals(1, viewModel.suppliers().size());
        assertEquals("Proveedores: 1", viewModel.supplierCountProperty().get());
    }

    @Test
    @DisplayName("Alta válida: inserta el proveedor activo con teléfono y correo")
    void createValidSupplier() throws Exception {
        fillValidForm();

        viewModel.save();

        assertEquals("", viewModel.errorMessageProperty().get());
        assertTrue(viewModel.statusMessageProperty().get().contains("guardado"));
        Supplier created = fakeRepository.findByName("Globex");
        assertEquals("600333444", created.getPhone());
        assertEquals("globex@example.com", created.getEmail());
        assertTrue(created.isActive());
    }

    @Test
    @DisplayName("Teléfono y correo vacíos se guardan como nulos")
    void blankContactIsSavedAsNull() throws Exception {
        viewModel.nameProperty().set("Globex");

        viewModel.save();

        Supplier created = fakeRepository.findByName("Globex");
        assertNull(created.getPhone());
        assertNull(created.getEmail());
    }

    @Test
    @DisplayName("Nombre vacío: error de campo y no se toca la base de datos")
    void emptyNameShowsFieldError() throws Exception {
        viewModel.phoneProperty().set("600333444");

        viewModel.save();

        assertEquals("Introduce el nombre del proveedor.", viewModel.nameErrorProperty().get());
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
    @DisplayName("Teléfono de más de 20 caracteres: error de campo")
    void phoneTooLongShowsFieldError() {
        viewModel.nameProperty().set("Globex");
        viewModel.phoneProperty().set("1".repeat(21));

        viewModel.save();

        assertEquals("El teléfono no puede superar los 20 caracteres.",
                viewModel.phoneErrorProperty().get());
    }

    @Test
    @DisplayName("Teléfono con caracteres no válidos: error de campo")
    void invalidPhoneShowsFieldError() {
        viewModel.nameProperty().set("Globex");
        viewModel.phoneProperty().set("llámame");

        viewModel.save();

        assertEquals("El teléfono solo puede contener números y los signos +, -, ( ), punto o espacios.",
                viewModel.phoneErrorProperty().get());
    }

    @Test
    @DisplayName("Correo con formato inválido: error de campo")
    void invalidEmailShowsFieldError() {
        viewModel.nameProperty().set("Globex");
        viewModel.emailProperty().set("no-es-un-correo");

        viewModel.save();

        assertEquals("Introduce un correo electrónico válido.", viewModel.emailErrorProperty().get());
    }

    @Test
    @DisplayName("Correo de más de 100 caracteres: error de campo")
    void emailTooLongShowsFieldError() {
        viewModel.nameProperty().set("Globex");
        viewModel.emailProperty().set("a".repeat(100) + "@example.com");

        viewModel.save();

        assertEquals("El correo no puede superar los 100 caracteres.",
                viewModel.emailErrorProperty().get());
    }

    @Test
    @DisplayName("Nombre duplicado: error de campo y no se inserta")
    void duplicatedNameShowsFieldError() throws Exception {
        viewModel.nameProperty().set("Acme");

        viewModel.save();

        assertEquals("El proveedor 'Acme' ya existe.", viewModel.nameErrorProperty().get());
        assertEquals(1, fakeRepository.findAll().size());
    }

    @Test
    @DisplayName("Nombre duplicado con otra capitalización: se rechaza")
    void duplicatedNameIsCaseInsensitive() {
        viewModel.nameProperty().set("acme");

        viewModel.save();

        assertEquals("El proveedor 'acme' ya existe.", viewModel.nameErrorProperty().get());
    }

    @Test
    @DisplayName("El nombre de un proveedor dado de baja sigue reservado: se rechaza el duplicado")
    void duplicatedNameOfInactiveSupplierIsRejected() throws Exception {
        // UNIQUE(name) is global, so a deactivated supplier still owns its name
        // and has to be reactivated instead of duplicated.
        fakeRepository = new FakeSupplierRepository()
                .withSupplier(new Supplier(7, "Acme", null, null, false));
        viewModel = new SuppliersViewModel(fakeRepository);
        viewModel.load();
        viewModel.nameProperty().set("Acme");

        viewModel.save();

        assertEquals("El proveedor 'Acme' ya existe.", viewModel.nameErrorProperty().get());
        assertEquals(1, fakeRepository.findAll().size());
    }

    @Test
    @DisplayName("Un duplicado detectado por la clave única al insertar se reporta como duplicado")
    void integrityViolationOnInsertIsReportedAsDuplicate() {
        FakeSupplierRepository racing = new FakeSupplierRepository() {
            @Override
            public Supplier findByName(String name) {
                return null;
            }

            @Override
            public int insert(Supplier supplier) throws SQLException {
                throw new SQLIntegrityConstraintViolationException(
                        "Duplicate entry for key 'uq_supplier_name'");
            }
        };
        SuppliersViewModel racingViewModel = new SuppliersViewModel(racing);
        racingViewModel.nameProperty().set("Acme");

        racingViewModel.save();

        assertEquals("El proveedor 'Acme' ya existe.", racingViewModel.nameErrorProperty().get());
    }

    @Test
    @DisplayName("Editar conserva id y estado del proveedor")
    void editPreservesIdAndStatus() throws Exception {
        Supplier retired = new Supplier(2, "Antiguo", null, null, false);
        fakeRepository.withSupplier(retired);
        viewModel.showInactiveProperty().set(true);
        viewModel.load();
        viewModel.selectedSupplierProperty().set(viewModel.suppliers().get(0));

        viewModel.nameProperty().set("Renombrado");
        viewModel.save();

        Supplier updated = fakeRepository.findByName("Renombrado");
        assertEquals(2, updated.getId());
        assertFalse(updated.isActive());
    }

    @Test
    @DisplayName("Editar a un nombre ya existente: error de duplicado")
    void editToExistingNameFails() throws Exception {
        fakeRepository.withSupplier(new Supplier(2, "Globex", null, null));
        viewModel.load();
        viewModel.selectedSupplierProperty().set(viewModel.suppliers().stream()
                .filter(supplier -> supplier.getName().equals("Globex"))
                .findFirst().orElseThrow());

        viewModel.nameProperty().set("Acme");
        viewModel.save();

        assertEquals("El proveedor 'Acme' ya existe.", viewModel.nameErrorProperty().get());
    }

    @Test
    @DisplayName("Dar de baja: el proveedor queda inactivo (baja lógica)")
    void deactivateMakesSupplierInactive() throws Exception {
        viewModel.selectedSupplierProperty().set(viewModel.suppliers().get(0));

        viewModel.deactivate();

        assertFalse(fakeRepository.findByName("Acme").isActive());
        assertTrue(viewModel.statusMessageProperty().get().contains("baja"));
        assertEquals("", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("Reactivar: un proveedor inactivo vuelve a estar activo")
    void activateMakesSupplierActiveAgain() throws Exception {
        fakeRepository.withSupplier(new Supplier(2, "Antiguo", null, null, false));
        viewModel.showInactiveProperty().set(true);
        viewModel.load();
        viewModel.selectedSupplierProperty().set(viewModel.suppliers().get(0));

        viewModel.activate();

        assertTrue(fakeRepository.findByName("Antiguo").isActive());
        assertTrue(viewModel.statusMessageProperty().get().contains("reactivado"));
    }

    @Test
    @DisplayName("Dar de baja sin selección muestra error")
    void deactivateWithoutSelectionFails() {
        viewModel.selectedSupplierProperty().set(null);

        viewModel.deactivate();

        assertEquals("Selecciona un proveedor de la tabla.",
                viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("Dar de baja un proveedor ya inactivo avisa sin tocar la base de datos")
    void deactivatingAnInactiveSupplierFails() throws Exception {
        fakeRepository.withSupplier(new Supplier(2, "Antiguo", null, null, false));
        viewModel.showInactiveProperty().set(true);
        viewModel.load();
        viewModel.selectedSupplierProperty().set(viewModel.suppliers().get(0));

        viewModel.deactivate();

        assertEquals("El proveedor ya está dado de baja.", viewModel.errorMessageProperty().get());
        assertFalse(fakeRepository.findByName("Antiguo").isActive());
    }

    @Test
    @DisplayName("Reactivar un proveedor ya activo avisa sin tocar la base de datos")
    void activatingAnActiveSupplierFails() throws Exception {
        viewModel.selectedSupplierProperty().set(viewModel.suppliers().get(0));

        viewModel.activate();

        assertEquals("El proveedor ya está activo.", viewModel.errorMessageProperty().get());
        assertTrue(fakeRepository.findByName("Acme").isActive());
    }

    @Test
    @DisplayName("Mostrar inactivos recarga la lista con los proveedores dados de baja")
    void showInactiveReloadsTheList() throws Exception {
        fakeRepository.withSupplier(new Supplier(2, "Antiguo", null, null, false));
        viewModel.load();
        assertEquals(1, viewModel.suppliers().size());

        viewModel.showInactiveProperty().set(true);

        assertEquals(1, viewModel.suppliers().size());
        assertEquals("Antiguo", viewModel.suppliers().get(0).getName());

        viewModel.showInactiveProperty().set(false);
        assertEquals("Acme", viewModel.suppliers().get(0).getName());
    }

    @Test
    @DisplayName("La búsqueda por nombre acota la tabla")
    void searchFiltersByName() throws Exception {
        fakeRepository.withSupplier(new Supplier(2, "Globex", null, null));
        viewModel.load();

        viewModel.searchTextProperty().set("glo");

        assertEquals(1, viewModel.filteredSuppliers().size());
        assertEquals("Globex", viewModel.filteredSuppliers().get(0).getName());

        viewModel.searchTextProperty().set("");
        assertEquals(2, viewModel.filteredSuppliers().size());
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

        assertEquals("Globex", fakeRepository.findByName("Globex").getName());
        assertTrue(viewModel.errorMessageProperty().get().contains("se guardaron"));
    }

    @Test
    @DisplayName("La validación en vivo solo actúa tras el primer intento de guardado")
    void liveValidationStartsAfterFirstSaveAttempt() {
        viewModel.nameProperty().set("");
        assertTrue(viewModel.nameErrorProperty().get().isEmpty());

        viewModel.save();
        assertEquals("Introduce el nombre del proveedor.", viewModel.nameErrorProperty().get());

        viewModel.nameProperty().set("Globex");
        assertTrue(viewModel.nameErrorProperty().get().isEmpty());
    }

    @Test
    @DisplayName("Nuevo proveedor limpia el formulario y los errores de campo")
    void newSupplierResetsTheForm() throws Exception {
        viewModel.selectedSupplierProperty().set(viewModel.suppliers().get(0));
        assertEquals("Editar proveedor", viewModel.formTitleProperty().get());

        viewModel.save();
        viewModel.newSupplier();

        assertNull(viewModel.selectedSupplierProperty().get());
        assertEquals("Nuevo proveedor", viewModel.formTitleProperty().get());
        assertEquals("", viewModel.nameProperty().get());
        assertEquals("", viewModel.phoneProperty().get());
        assertEquals("", viewModel.emailProperty().get());
        assertTrue(viewModel.nameErrorProperty().get().isEmpty());
    }

    @Test
    @DisplayName("Seleccionar un proveedor carga sus datos en el formulario")
    void selectingASupplierFillsTheForm() {
        viewModel.selectedSupplierProperty().set(viewModel.suppliers().get(0));

        assertEquals("Acme", viewModel.nameProperty().get());
        assertEquals("600111222", viewModel.phoneProperty().get());
        assertEquals("acme@example.com", viewModel.emailProperty().get());
        assertEquals("Editar proveedor", viewModel.formTitleProperty().get());
    }

    @Test
    @DisplayName("El estado del botón baja/reactivar depende del proveedor seleccionado")
    void toggleStateFollowsSelection() throws Exception {
        fakeRepository.withSupplier(new Supplier(2, "Antiguo", null, null, false));
        viewModel.load();

        assertTrue(viewModel.toggleDisabledProperty().get());
        assertEquals(SuppliersViewModel.TOGGLE_DEACTIVATE_LABEL,
                viewModel.toggleLabelProperty().get());

        viewModel.selectedSupplierProperty().set(viewModel.suppliers().get(0));
        assertFalse(viewModel.toggleDisabledProperty().get());
        assertEquals(SuppliersViewModel.TOGGLE_DEACTIVATE_LABEL,
                viewModel.toggleLabelProperty().get());

        // Switching to the inactive list drops the (active) selection; selecting
        // the inactive supplier offers reactivation.
        viewModel.showInactiveProperty().set(true);
        assertTrue(viewModel.toggleDisabledProperty().get());
        viewModel.selectedSupplierProperty().set(viewModel.suppliers().get(0));
        assertEquals(SuppliersViewModel.TOGGLE_ACTIVATE_LABEL,
                viewModel.toggleLabelProperty().get());
        assertFalse(viewModel.toggleDisabledProperty().get());
    }
}
