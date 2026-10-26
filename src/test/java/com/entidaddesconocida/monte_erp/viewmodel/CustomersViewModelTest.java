package com.entidaddesconocida.monte_erp.viewmodel;

import com.entidaddesconocida.monte_erp.domain.model.Customer;
import com.entidaddesconocida.monte_erp.viewmodel.support.FakeCustomerRepository;

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
 * Unit tests for {@link CustomersViewModel} (RF-06 / CU-06).
 *
 * <p>No database and no JavaFX toolkit: the ViewModel is built by hand with an
 * in-memory repository. The per-field validation properties replace ValidatorFX
 * here, so the rules are asserted without a toolkit.</p>
 */
class CustomersViewModelTest {

    private static final Customer ACME =
            new Customer(1, "Acme", "600111222", "acme@example.com", "Calle Mayor 1");

    private FakeCustomerRepository fakeRepository;
    private CustomersViewModel viewModel;

    @BeforeEach
    void setUp() {
        fakeRepository = new FakeCustomerRepository().withCustomer(ACME);
        viewModel = new CustomersViewModel(fakeRepository);
        viewModel.load();
    }

    private void fillValidForm() {
        viewModel.nameProperty().set("Globex");
        viewModel.phoneProperty().set("600333444");
        viewModel.emailProperty().set("globex@example.com");
        viewModel.addressProperty().set("Avenida Central 5");
    }

    @Test
    @DisplayName("load carga los clientes y el contador")
    void loadFillsTheListAndCount() {
        assertEquals(1, viewModel.customers().size());
        assertEquals("Clientes: 1", viewModel.customerCountProperty().get());
    }

    @Test
    @DisplayName("Alta válida: inserta el cliente activo con teléfono, correo y dirección")
    void createValidCustomer() throws Exception {
        fillValidForm();

        viewModel.save();

        assertEquals("", viewModel.errorMessageProperty().get());
        assertTrue(viewModel.statusMessageProperty().get().contains("guardado"));
        Customer created = fakeRepository.findByName("Globex");
        assertEquals("600333444", created.getPhone());
        assertEquals("globex@example.com", created.getEmail());
        assertEquals("Avenida Central 5", created.getAddress());
        assertTrue(created.isActive());
    }

    @Test
    @DisplayName("Teléfono, correo y dirección vacíos se guardan como nulos")
    void blankContactIsSavedAsNull() throws Exception {
        viewModel.nameProperty().set("Globex");

        viewModel.save();

        Customer created = fakeRepository.findByName("Globex");
        assertNull(created.getPhone());
        assertNull(created.getEmail());
        assertNull(created.getAddress());
    }

    @Test
    @DisplayName("Nombre vacío: error de campo y no se toca la base de datos")
    void emptyNameShowsFieldError() throws Exception {
        viewModel.phoneProperty().set("600333444");

        viewModel.save();

        assertEquals("Introduce el nombre del cliente.", viewModel.nameErrorProperty().get());
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
    @DisplayName("Dirección de más de 255 caracteres: error de campo")
    void addressTooLongShowsFieldError() {
        viewModel.nameProperty().set("Globex");
        viewModel.addressProperty().set("a".repeat(256));

        viewModel.save();

        assertEquals("La dirección no puede superar los 255 caracteres.",
                viewModel.addressErrorProperty().get());
    }

    @Test
    @DisplayName("Nombre duplicado: error de campo y no se inserta")
    void duplicatedNameShowsFieldError() throws Exception {
        viewModel.nameProperty().set("Acme");

        viewModel.save();

        assertEquals("El cliente 'Acme' ya existe.", viewModel.nameErrorProperty().get());
        assertEquals(1, fakeRepository.findAll().size());
    }

    @Test
    @DisplayName("Nombre duplicado con otra capitalización: se rechaza")
    void duplicatedNameIsCaseInsensitive() {
        viewModel.nameProperty().set("acme");

        viewModel.save();

        assertEquals("El cliente 'acme' ya existe.", viewModel.nameErrorProperty().get());
    }

    @Test
    @DisplayName("El nombre de un cliente dado de baja sigue reservado: se rechaza el duplicado")
    void duplicatedNameOfInactiveCustomerIsRejected() throws Exception {
        // UNIQUE(name) is global, so a deactivated customer still owns its name
        // and has to be reactivated instead of duplicated.
        fakeRepository = new FakeCustomerRepository()
                .withCustomer(new Customer(7, "Acme", null, null, null, false));
        viewModel = new CustomersViewModel(fakeRepository);
        viewModel.load();
        viewModel.nameProperty().set("Acme");

        viewModel.save();

        assertEquals("El cliente 'Acme' ya existe.", viewModel.nameErrorProperty().get());
        assertEquals(1, fakeRepository.findAll().size());
    }

    @Test
    @DisplayName("Un duplicado detectado por la clave única al insertar se reporta como duplicado")
    void integrityViolationOnInsertIsReportedAsDuplicate() {
        FakeCustomerRepository racing = new FakeCustomerRepository() {
            @Override
            public Customer findByName(String name) {
                return null;
            }

            @Override
            public int insert(Customer customer) throws SQLException {
                throw new SQLIntegrityConstraintViolationException(
                        "Duplicate entry for key 'uq_customer_name'");
            }
        };
        CustomersViewModel racingViewModel = new CustomersViewModel(racing);
        racingViewModel.nameProperty().set("Acme");

        racingViewModel.save();

        assertEquals("El cliente 'Acme' ya existe.", racingViewModel.nameErrorProperty().get());
    }

    @Test
    @DisplayName("Editar conserva id y estado del cliente")
    void editPreservesIdAndStatus() throws Exception {
        Customer retired = new Customer(2, "Antiguo", null, null, null, false);
        fakeRepository.withCustomer(retired);
        viewModel.showInactiveProperty().set(true);
        viewModel.load();
        viewModel.selectedCustomerProperty().set(viewModel.customers().get(0));

        viewModel.nameProperty().set("Renombrado");
        viewModel.save();

        Customer updated = fakeRepository.findByName("Renombrado");
        assertEquals(2, updated.getId());
        assertFalse(updated.isActive());
    }

    @Test
    @DisplayName("Editar a un nombre ya existente: error de duplicado")
    void editToExistingNameFails() throws Exception {
        fakeRepository.withCustomer(new Customer(2, "Globex", null, null, null));
        viewModel.load();
        viewModel.selectedCustomerProperty().set(viewModel.customers().stream()
                .filter(customer -> customer.getName().equals("Globex"))
                .findFirst().orElseThrow());

        viewModel.nameProperty().set("Acme");
        viewModel.save();

        assertEquals("El cliente 'Acme' ya existe.", viewModel.nameErrorProperty().get());
    }

    @Test
    @DisplayName("Dar de baja: el cliente queda inactivo (baja lógica)")
    void deactivateMakesCustomerInactive() throws Exception {
        viewModel.selectedCustomerProperty().set(viewModel.customers().get(0));

        viewModel.deactivate();

        assertFalse(fakeRepository.findByName("Acme").isActive());
        assertTrue(viewModel.statusMessageProperty().get().contains("baja"));
        assertEquals("", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("Reactivar: un cliente inactivo vuelve a estar activo")
    void activateMakesCustomerActiveAgain() throws Exception {
        fakeRepository.withCustomer(new Customer(2, "Antiguo", null, null, null, false));
        viewModel.showInactiveProperty().set(true);
        viewModel.load();
        viewModel.selectedCustomerProperty().set(viewModel.customers().get(0));

        viewModel.activate();

        assertTrue(fakeRepository.findByName("Antiguo").isActive());
        assertTrue(viewModel.statusMessageProperty().get().contains("reactivado"));
    }

    @Test
    @DisplayName("Dar de baja sin selección muestra error")
    void deactivateWithoutSelectionFails() {
        viewModel.selectedCustomerProperty().set(null);

        viewModel.deactivate();

        assertEquals("Selecciona un cliente de la tabla.",
                viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("Dar de baja un cliente ya inactivo avisa sin tocar la base de datos")
    void deactivatingAnInactiveCustomerFails() throws Exception {
        fakeRepository.withCustomer(new Customer(2, "Antiguo", null, null, null, false));
        viewModel.showInactiveProperty().set(true);
        viewModel.load();
        viewModel.selectedCustomerProperty().set(viewModel.customers().get(0));

        viewModel.deactivate();

        assertEquals("El cliente ya está dado de baja.", viewModel.errorMessageProperty().get());
        assertFalse(fakeRepository.findByName("Antiguo").isActive());
    }

    @Test
    @DisplayName("Reactivar un cliente ya activo avisa sin tocar la base de datos")
    void activatingAnActiveCustomerFails() throws Exception {
        viewModel.selectedCustomerProperty().set(viewModel.customers().get(0));

        viewModel.activate();

        assertEquals("El cliente ya está activo.", viewModel.errorMessageProperty().get());
        assertTrue(fakeRepository.findByName("Acme").isActive());
    }

    @Test
    @DisplayName("Mostrar inactivos recarga la lista con los clientes dados de baja")
    void showInactiveReloadsTheList() throws Exception {
        fakeRepository.withCustomer(new Customer(2, "Antiguo", null, null, null, false));
        viewModel.load();
        assertEquals(1, viewModel.customers().size());

        viewModel.showInactiveProperty().set(true);

        assertEquals(1, viewModel.customers().size());
        assertEquals("Antiguo", viewModel.customers().get(0).getName());

        viewModel.showInactiveProperty().set(false);
        assertEquals("Acme", viewModel.customers().get(0).getName());
    }

    @Test
    @DisplayName("La búsqueda por nombre acota la tabla")
    void searchFiltersByName() throws Exception {
        fakeRepository.withCustomer(new Customer(2, "Globex", null, null, null));
        viewModel.load();

        viewModel.searchTextProperty().set("glo");

        assertEquals(1, viewModel.filteredCustomers().size());
        assertEquals("Globex", viewModel.filteredCustomers().get(0).getName());

        viewModel.searchTextProperty().set("");
        assertEquals(2, viewModel.filteredCustomers().size());
    }

    @Test
    @DisplayName("La búsqueda por teléfono acota la tabla")
    void searchFiltersByPhone() throws Exception {
        fakeRepository.withCustomer(new Customer(2, "Globex", "600333444", null, null));
        viewModel.load();

        viewModel.searchTextProperty().set("600333444");

        assertEquals(1, viewModel.filteredCustomers().size());
        assertEquals("Globex", viewModel.filteredCustomers().get(0).getName());
    }

    @Test
    @DisplayName("La búsqueda por correo acota la tabla")
    void searchFiltersByEmail() throws Exception {
        fakeRepository.withCustomer(new Customer(2, "Globex", null, "globex@example.com", null));
        viewModel.load();

        viewModel.searchTextProperty().set("globex@");

        assertEquals(1, viewModel.filteredCustomers().size());
        assertEquals("Globex", viewModel.filteredCustomers().get(0).getName());
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
        assertEquals("Introduce el nombre del cliente.", viewModel.nameErrorProperty().get());

        viewModel.nameProperty().set("Globex");
        assertTrue(viewModel.nameErrorProperty().get().isEmpty());
    }

    @Test
    @DisplayName("Nuevo cliente limpia el formulario y los errores de campo")
    void newCustomerResetsTheForm() throws Exception {
        viewModel.selectedCustomerProperty().set(viewModel.customers().get(0));
        assertEquals("Editar cliente", viewModel.formTitleProperty().get());

        viewModel.save();
        viewModel.newCustomer();

        assertNull(viewModel.selectedCustomerProperty().get());
        assertEquals("Nuevo cliente", viewModel.formTitleProperty().get());
        assertEquals("", viewModel.nameProperty().get());
        assertEquals("", viewModel.phoneProperty().get());
        assertEquals("", viewModel.emailProperty().get());
        assertEquals("", viewModel.addressProperty().get());
        assertTrue(viewModel.nameErrorProperty().get().isEmpty());
    }

    @Test
    @DisplayName("Seleccionar un cliente carga sus datos en el formulario")
    void selectingACustomerFillsTheForm() {
        viewModel.selectedCustomerProperty().set(viewModel.customers().get(0));

        assertEquals("Acme", viewModel.nameProperty().get());
        assertEquals("600111222", viewModel.phoneProperty().get());
        assertEquals("acme@example.com", viewModel.emailProperty().get());
        assertEquals("Calle Mayor 1", viewModel.addressProperty().get());
        assertEquals("Editar cliente", viewModel.formTitleProperty().get());
    }

    @Test
    @DisplayName("El estado del botón baja/reactivar depende del cliente seleccionado")
    void toggleStateFollowsSelection() throws Exception {
        fakeRepository.withCustomer(new Customer(2, "Antiguo", null, null, null, false));
        viewModel.load();

        assertTrue(viewModel.toggleDisabledProperty().get());
        assertEquals(CustomersViewModel.TOGGLE_DEACTIVATE_LABEL,
                viewModel.toggleLabelProperty().get());

        viewModel.selectedCustomerProperty().set(viewModel.customers().get(0));
        assertFalse(viewModel.toggleDisabledProperty().get());
        assertEquals(CustomersViewModel.TOGGLE_DEACTIVATE_LABEL,
                viewModel.toggleLabelProperty().get());

        // Switching to the inactive list drops the (active) selection; selecting
        // the inactive customer offers reactivation.
        viewModel.showInactiveProperty().set(true);
        assertTrue(viewModel.toggleDisabledProperty().get());
        viewModel.selectedCustomerProperty().set(viewModel.customers().get(0));
        assertEquals(CustomersViewModel.TOGGLE_ACTIVATE_LABEL,
                viewModel.toggleLabelProperty().get());
        assertFalse(viewModel.toggleDisabledProperty().get());
    }
}
