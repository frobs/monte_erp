package com.entidaddesconocida.monte_erp.viewmodel;

import com.entidaddesconocida.monte_erp.domain.model.User;
import com.entidaddesconocida.monte_erp.security.PasswordHasher;
import com.entidaddesconocida.monte_erp.viewmodel.support.FakeUserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link LoginViewModel}.
 *
 * <p>No database, no JavaFX toolkit: the ViewModel is constructed by hand with
 * a fake repository, which constructor injection makes trivial.</p>
 */
class LoginViewModelTest {

    private static final String VALID_USER = "ana";
    private static final String VALID_PASSWORD = "ana123";
    private static final String VALID_HASH = PasswordHasher.hash(VALID_PASSWORD);

    private FakeUserRepository fakeRepository;
    private LoginViewModel viewModel;

    @BeforeEach
    void setUp() {
        fakeRepository = new FakeUserRepository()
                .withUser(new User("Ana", VALID_USER, VALID_HASH, User.ROLE_ADMIN, true));
        viewModel = new LoginViewModel(fakeRepository);
    }

    @Test
    @DisplayName("Campos vacíos: muestra mensaje de error")
    void emptyFieldsShowError() {
        viewModel.login();
        assertEquals("Introduce usuario y contraseña.", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("Usuario inexistente: mismo mensaje genérico que contraseña errónea")
    void unknownUserShowsGenericError() {
        viewModel.usernameProperty().set("nobody");
        viewModel.passwordProperty().set("any");
        viewModel.login();
        assertEquals("Usuario o contraseña incorrectos.", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("Contraseña errónea: mismo mensaje genérico y campo contraseña vacío")
    void wrongPasswordClearsPasswordField() {
        viewModel.usernameProperty().set(VALID_USER);
        viewModel.passwordProperty().set("wrong");
        viewModel.login();
        assertEquals("Usuario o contraseña incorrectos.", viewModel.errorMessageProperty().get());
        assertTrue(viewModel.passwordProperty().isEmpty().get());
    }

    @Test
    @DisplayName("Usuario desactivado: mensaje de desactivado")
    void deactivatedUserShowsDeactivatedMessage() {
        fakeRepository.withUser(new User("Bob", "bob", PasswordHasher.hash("bob123"), User.ROLE_EMPLOYEE, false));
        viewModel.usernameProperty().set("bob");
        viewModel.passwordProperty().set("bob123");
        viewModel.login();
        assertEquals("El usuario está desactivado. Contacta con un administrador.", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("Credenciales válidas: publica LOGIN_SUCCEEDED con el usuario correcto")
    void validCredentialsPublishSuccess() {
        viewModel.usernameProperty().set(VALID_USER);
        viewModel.passwordProperty().set(VALID_PASSWORD);

        AtomicReference<User> captured = new AtomicReference<>();
        viewModel.subscribe(LoginViewModel.LOGIN_SUCCEEDED, (key, payload) -> captured.set((User) payload[0]));

        viewModel.login();

        assertEquals("", viewModel.errorMessageProperty().get());
        assertNotNull(captured.get());
        assertEquals(VALID_USER, captured.get().getUsername());
        assertTrue(captured.get().isAdmin());
    }

    @Test
    @DisplayName("SQLException del repositorio: mensaje de error de base de datos")
    void databaseErrorShowsMessage() {
        fakeRepository.throwOnAccess();
        viewModel.usernameProperty().set(VALID_USER);
        viewModel.passwordProperty().set(VALID_PASSWORD);
        viewModel.login();
        assertTrue(viewModel.errorMessageProperty().get().contains("No se pudo conectar"));
    }

    @Test
    @DisplayName("Propiedades a null: se tratan como campos vacíos")
    void nullPropertiesCountAsEmptyFields() {
        viewModel.usernameProperty().set(null);
        viewModel.passwordProperty().set(null);
        viewModel.login();
        assertEquals("Introduce usuario y contraseña.", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("clearError borra el mensaje visible")
    void clearErrorRemovesTheMessage() {
        viewModel.login();
        assertFalse(viewModel.errorMessageProperty().get().isEmpty());

        viewModel.clearError();

        assertEquals("", viewModel.errorMessageProperty().get());
    }
}
