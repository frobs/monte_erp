package com.entidaddesconocida.monte_erp.viewmodel;

import com.entidaddesconocida.monte_erp.domain.model.ConnectionSettings;
import com.entidaddesconocida.monte_erp.viewmodel.support.FakeSetupService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link SetupViewModel} with a fake {@code SetupService}: no
 * database, no keyring, no JavaFX toolkit.
 */
class SetupViewModelTest {

    private static final ConnectionSettings VALID =
            new ConnectionSettings("jdbc:mysql://localhost:3306/monte_erp", "erp_user", "erp_pass");

    private SetupViewModel onboardingViewModel(FakeSetupService service) {
        return new SetupViewModel(service, true);
    }

    private SetupViewModel editViewModel(FakeSetupService service) {
        return new SetupViewModel(service, false);
    }

    private void fillConnection(SetupViewModel vm) {
        vm.urlProperty().set(VALID.url());
        vm.dbUserProperty().set(VALID.user());
        vm.dbPasswordProperty().set(VALID.password());
    }

    @Test
    @DisplayName("Campos de conexión vacíos: muestra mensaje de error")
    void emptyConnectionFieldsShowError() {
        SetupViewModel vm = onboardingViewModel(new FakeSetupService());
        vm.save();
        assertEquals("Introduce la URL, el usuario y la contraseña.", vm.errorMessageProperty().get());
    }

    @Test
    @DisplayName("En onboarding, sin datos de admin muestra mensaje de error")
    void onboardingRequiresAdminCredentials() {
        SetupViewModel vm = onboardingViewModel(new FakeSetupService());
        fillConnection(vm);
        vm.save();
        assertEquals("Introduce el nombre, el usuario y la contraseña del administrador.",
                vm.errorMessageProperty().get());
    }

    @Test
    @DisplayName("En onboarding, sin nombre de admin muestra mensaje de error")
    void onboardingRequiresAdminName() {
        SetupViewModel vm = onboardingViewModel(new FakeSetupService());
        fillConnection(vm);
        vm.adminUsernameProperty().set("admin");
        vm.adminPasswordProperty().set("admin123");
        vm.save();
        assertEquals("Introduce el nombre, el usuario y la contraseña del administrador.",
                vm.errorMessageProperty().get());
    }

    @Test
    @DisplayName("En onboarding, guardar llama a provision y publica SETUP_SUCCEEDED")
    void onboardingSaveProvisionsAndPublishes() {
        FakeSetupService service = new FakeSetupService();
        SetupViewModel vm = onboardingViewModel(service);
        fillConnection(vm);
        vm.adminNameProperty().set("Administrador");
        vm.adminUsernameProperty().set("admin");
        vm.adminPasswordProperty().set("admin123");

        AtomicBoolean published = new AtomicBoolean(false);
        vm.subscribe(SetupViewModel.SETUP_SUCCEEDED, (key, payload) -> published.set(true));

        vm.save();

        assertTrue(published.get());
        assertEquals("Administrador", service.lastAdminName());
        assertEquals("admin", service.lastAdminUsername());
        assertEquals("admin123", service.lastAdminPassword());
        assertEquals(VALID, service.lastSaved());
        assertEquals("", vm.errorMessageProperty().get());
    }

    @Test
    @DisplayName("En modo edición, guardar llama a save (sin admin) y publica SETUP_SUCCEEDED")
    void editSavePersistsAndPublishes() {
        FakeSetupService service = new FakeSetupService();
        SetupViewModel vm = editViewModel(service);
        fillConnection(vm);

        AtomicBoolean published = new AtomicBoolean(false);
        vm.subscribe(SetupViewModel.SETUP_SUCCEEDED, (key, payload) -> published.set(true));

        vm.save();

        assertTrue(published.get());
        assertNull(service.lastAdminUsername());
        assertEquals(VALID, service.lastSaved());
    }

    @Test
    @DisplayName("Falló al guardar: mensaje de error y no publica SETUP_SUCCEEDED")
    void saveFailureShowsErrorAndDoesNotPublish() {
        FakeSetupService service = new FakeSetupService().throwOnProvision();
        SetupViewModel vm = onboardingViewModel(service);
        fillConnection(vm);
        vm.adminNameProperty().set("Administrador");
        vm.adminUsernameProperty().set("admin");
        vm.adminPasswordProperty().set("admin123");

        AtomicBoolean published = new AtomicBoolean(false);
        vm.subscribe(SetupViewModel.SETUP_SUCCEEDED, (key, payload) -> published.set(true));

        vm.save();

        assertFalse(published.get());
        assertTrue(vm.errorMessageProperty().get().contains("No se pudo guardar la configuración"));
    }

    @Test
    @DisplayName("Probar conexión correcta muestra el estado de éxito")
    void testConnectionSuccess() {
        SetupViewModel vm = editViewModel(new FakeSetupService());
        fillConnection(vm);
        vm.testConnection();
        assertEquals("Conexión correcta.", vm.statusMessageProperty().get());
        assertEquals("", vm.errorMessageProperty().get());
    }

    @Test
    @DisplayName("Probar conexión fallida muestra el mensaje de error")
    void testConnectionFailure() {
        SetupViewModel vm = editViewModel(new FakeSetupService().throwOnTestConnection());
        fillConnection(vm);
        vm.testConnection();
        assertEquals("", vm.statusMessageProperty().get());
        assertTrue(vm.errorMessageProperty().get().contains("No se pudo conectar"));
    }

    @Test
    @DisplayName("Probar conexión con URL mal formada: error local sin llamar al servicio")
    void testConnectionWithMalformedUrlDoesNotCallTheService() {
        FakeSetupService service = new FakeSetupService();
        SetupViewModel vm = editViewModel(service);
        vm.urlProperty().set("localhost:3306/monte_erp");
        vm.dbUserProperty().set(VALID.user());
        vm.dbPasswordProperty().set(VALID.password());

        vm.testConnection();

        assertEquals("La URL no es válida. Debe tener el formato "
                + "jdbc:mysql://host:puerto/base_de_datos.", vm.errorMessageProperty().get());
        assertEquals("", vm.statusMessageProperty().get());
        assertNull(service.lastTested());
    }

    @Test
    @DisplayName("Guardar con URL sin base de datos: error local sin llamar al servicio")
    void saveWithMalformedUrlDoesNotCallTheService() {
        FakeSetupService service = new FakeSetupService();
        SetupViewModel vm = editViewModel(service);
        vm.urlProperty().set("jdbc:mysql://localhost:3306");
        vm.dbUserProperty().set(VALID.user());
        vm.dbPasswordProperty().set(VALID.password());

        vm.save();

        assertTrue(vm.errorMessageProperty().get().startsWith("La URL no es válida."));
        assertNull(service.lastSaved());
    }

    @Test
    @DisplayName("URL con puerto no numérico, negativo, cero o fuera de rango: error local")
    void invalidPortIsRejectedBeforeConnecting() {
        List<String> invalidUrls = List.of(
                "jdbc:mysql://localhost:abc/monte_erp",
                "jdbc:mysql://localhost:-3306/monte_erp",
                "jdbc:mysql://localhost:0/monte_erp",
                "jdbc:mysql://localhost:70000/monte_erp",
                "jdbc:mysql://localhost:3306.5/monte_erp",
                "jdbc:mysql://localhost:/monte_erp",
                "jdbc:mysql://[::1]:-1/monte_erp");

        for (String url : invalidUrls) {
            FakeSetupService service = new FakeSetupService();
            SetupViewModel vm = editViewModel(service);
            vm.urlProperty().set(url);
            vm.dbUserProperty().set(VALID.user());
            vm.dbPasswordProperty().set(VALID.password());

            vm.testConnection();

            assertTrue(vm.errorMessageProperty().get().startsWith("La URL no es válida."),
                    "URL: " + url);
            assertNull(service.lastTested(), "URL: " + url);
        }
    }

    @Test
    @DisplayName("URL sin puerto: se acepta y MySQL usa el puerto por defecto")
    void urlWithoutPortIsAccepted() {
        FakeSetupService service = new FakeSetupService();
        SetupViewModel vm = editViewModel(service);
        vm.urlProperty().set("jdbc:mysql://localhost/monte_erp");
        vm.dbUserProperty().set(VALID.user());
        vm.dbPasswordProperty().set(VALID.password());

        vm.testConnection();

        assertEquals("Conexión correcta.", vm.statusMessageProperty().get());
        assertEquals("", vm.errorMessageProperty().get());
    }

    @Test
    @DisplayName("URL con IPv6 entre corchetes y puerto: se acepta")
    void bracketedIpv6UrlIsAccepted() {
        FakeSetupService service = new FakeSetupService();
        SetupViewModel vm = editViewModel(service);
        vm.urlProperty().set("jdbc:mysql://[::1]:3306/monte_erp");
        vm.dbUserProperty().set(VALID.user());
        vm.dbPasswordProperty().set(VALID.password());

        vm.testConnection();

        assertEquals("Conexión correcta.", vm.statusMessageProperty().get());
        assertEquals("", vm.errorMessageProperty().get());
    }

    @Test
    @DisplayName("La configuración existente precarga URL y usuario pero oculta la contraseña")
    void prefillsNonSecretSettingsFromService() {
        FakeSetupService service = new FakeSetupService().withLoaded(VALID);
        SetupViewModel vm = editViewModel(service);
        assertEquals(VALID.url(), vm.urlProperty().get());
        assertEquals(VALID.user(), vm.dbUserProperty().get());
        assertEquals("", vm.dbPasswordProperty().get());
    }

    @Test
    @DisplayName("Reutiliza la contraseña guardada solo para el endpoint y usuario ya confiables")
    void testConnectionReusesSecretOnlyForStoredEndpoint() {
        FakeSetupService service = new FakeSetupService().withLoaded(VALID);
        SetupViewModel vm = editViewModel(service);

        vm.testConnection();

        assertEquals(VALID, service.lastTested());
        assertEquals("", vm.dbPasswordProperty().get());
    }

    @Test
    @DisplayName("Cambiar el endpoint exige volver a introducir la contraseña")
    void changedEndpointRequiresExplicitPassword() {
        FakeSetupService service = new FakeSetupService().withLoaded(VALID);
        SetupViewModel vm = editViewModel(service);
        vm.urlProperty().set("jdbc:mysql://other-host:3306/monte_erp");

        vm.testConnection();

        assertEquals("Introduce la URL, el usuario y la contraseña.", vm.errorMessageProperty().get());
        assertNull(service.lastTested());
    }

    @Test
    @DisplayName("Rechaza credenciales embebidas en la URL al probar y guardar")
    void credentialBearingUrlIsRejected() {
        FakeSetupService service = new FakeSetupService();
        SetupViewModel vm = editViewModel(service);
        vm.urlProperty().set("jdbc:mysql://localhost/monte_erp?password=exposed");
        vm.dbUserProperty().set(VALID.user());
        vm.dbPasswordProperty().set(VALID.password());

        vm.testConnection();
        assertTrue(vm.errorMessageProperty().get().contains("No incluyas credenciales"));
        assertNull(service.lastTested());

        vm.save();
        assertTrue(vm.errorMessageProperty().get().contains("No incluyas credenciales"));
        assertNull(service.lastSaved());
    }

    @Test
    @DisplayName("La sección de administrador solo es visible en onboarding")
    void adminSectionVisibilityFollowsMode() {
        assertTrue(onboardingViewModel(new FakeSetupService()).adminSectionVisibleProperty().get());
        assertFalse(editViewModel(new FakeSetupService()).adminSectionVisibleProperty().get());
    }

    @Test
    @DisplayName("Probar conexión sin todos los campos muestra error y limpia el estado")
    void testConnectionWithIncompleteFieldsShowsError() {
        SetupViewModel vm = editViewModel(new FakeSetupService());
        vm.urlProperty().set(VALID.url());
        vm.dbUserProperty().set(VALID.user());

        vm.testConnection();

        assertEquals("Introduce la URL, el usuario y la contraseña.", vm.errorMessageProperty().get());
        assertEquals("", vm.statusMessageProperty().get());
    }

    @Test
    @DisplayName("clearMessages limpia el error y el estado")
    void clearMessagesRemovesBothMessages() {
        SetupViewModel vm = editViewModel(new FakeSetupService().throwOnTestConnection());
        fillConnection(vm);
        vm.testConnection();
        assertFalse(vm.errorMessageProperty().get().isEmpty());

        vm.clearMessages();

        assertEquals("", vm.errorMessageProperty().get());
        assertEquals("", vm.statusMessageProperty().get());
    }
}
