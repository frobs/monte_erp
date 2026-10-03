package com.entidaddesconocida.monte_erp.viewmodel;

import com.entidaddesconocida.monte_erp.domain.model.User;
import com.entidaddesconocida.monte_erp.domain.repository.UserRepository;
import com.entidaddesconocida.monte_erp.security.PasswordHasher;

import de.saxsys.mvvmfx.ViewModel;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ViewModel of the sign-in screen (RF-01).
 *
 * <p>Holds the form state (username, password, error message) and performs the
 * authentication logic. It never references the View: on success it publishes a
 * notification that the {@code AppShell} (or tests) can observe.</p>
 */
public class LoginViewModel implements ViewModel {

    private static final Logger LOGGER = Logger.getLogger(LoginViewModel.class.getName());

    public static final String LOGIN_SUCCEEDED = "loginSucceeded";

    private final UserRepository userRepository;

    private final StringProperty username = new SimpleStringProperty("");
    private final StringProperty password = new SimpleStringProperty("");
    private final StringProperty errorMessage = new SimpleStringProperty("");

    public LoginViewModel(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** Username bound to the login form. */
    public StringProperty usernameProperty() {
        return username;
    }

    /** Password bound to the login form. */
    public StringProperty passwordProperty() {
        return password;
    }

    /** Error message displayed when authentication fails. */
    public StringProperty errorMessageProperty() {
        return errorMessage;
    }

    /** Attempts to authenticate with the current username and password. */
    public void login() {
        String u = username.get() == null ? "" : username.get().trim();
        String p = password.get() == null ? "" : password.get();

        if (u.isEmpty() || p.isEmpty()) {
            errorMessage.set("Introduce usuario y contraseña.");
            return;
        }

        try {
            User user = userRepository.findByUsername(u);

            // Same message for an unknown user and a wrong password: whether the
            // login name exists is never revealed.
            if (user == null || !PasswordHasher.verify(p, user.getPasswordHash())) {
                errorMessage.set("Usuario o contraseña incorrectos.");
                password.set("");
                return;
            }
            if (!user.isActive()) {
                errorMessage.set("El usuario está desactivado. Contacta con un administrador.");
                return;
            }

            password.set("");
            errorMessage.set("");
            publish(LOGIN_SUCCEEDED, user);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error during sign in", e);
            errorMessage.set("No se pudo conectar con la base de datos. Comprueba que MySQL esté arrancado.");
        }
    }

    /** Clears any visible error (useful on view initialisation). */
    public void clearError() {
        errorMessage.set("");
    }
}
