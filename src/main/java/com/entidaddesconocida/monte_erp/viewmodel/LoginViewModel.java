package com.entidaddesconocida.monte_erp.viewmodel;

import com.entidaddesconocida.monte_erp.app.BackgroundTasks;
import com.entidaddesconocida.monte_erp.domain.model.User;
import com.entidaddesconocida.monte_erp.domain.repository.UserRepository;
import com.entidaddesconocida.monte_erp.security.PasswordHasher;

import de.saxsys.mvvmfx.ViewModel;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.sql.SQLException;
import java.util.Optional;
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
    private static final String UNKNOWN_USER_HASH = PasswordHasher.hash("monte-erp-unknown-user");

    public static final String LOGIN_SUCCEEDED = "loginSucceeded";

    private final UserRepository userRepository;
    private final BackgroundTasks backgroundTasks;

    private final StringProperty username = new SimpleStringProperty("");
    private final StringProperty password = new SimpleStringProperty("");
    private final StringProperty errorMessage = new SimpleStringProperty("");

    public LoginViewModel(UserRepository userRepository) {
        this(userRepository, BackgroundTasks.synchronous());
    }

    /**
     * @param backgroundTasks executor of the authentication (PBKDF2 is
     *                        deliberately slow); {@link
     *                        BackgroundTasks#synchronous()} in tests and a
     *                        virtual-thread executor in the application
     */
    public LoginViewModel(UserRepository userRepository, BackgroundTasks backgroundTasks) {
        this.userRepository = userRepository;
        this.backgroundTasks = backgroundTasks;
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

        // The query and the key derivation run off the UI thread.
        backgroundTasks.submit(
                () -> authenticate(u, p),
                authenticated -> {
                    password.set("");
                    if (authenticated.isEmpty()) {
                        errorMessage.set("Usuario o contraseña incorrectos.");
                        return;
                    }
                    errorMessage.set("");
                    publish(LOGIN_SUCCEEDED, authenticated.get());
                },
                failure -> {
                    LOGGER.log(Level.SEVERE, "Database error during sign in", failure);
                    errorMessage.set(ViewMessages.DATABASE_ERROR);
                });
    }

    /**
     * Authenticates off the UI thread.
     *
     * <p>Always performs one PBKDF2 verification, including for missing and
     * disabled accounts, so neither the response nor the elapsed work reveals the
     * account state.</p>
     *
     * @return the signed-in user, or empty when the credentials are rejected
     */
    private Optional<User> authenticate(String username, String password) throws SQLException {
        User user = userRepository.findByUsername(username);
        String hash = user == null ? UNKNOWN_USER_HASH : user.getPasswordHash();
        boolean passwordMatches = PasswordHasher.verify(password, hash);
        if (user == null || !passwordMatches || !user.isActive()) {
            return Optional.empty();
        }
        return Optional.of(user);
    }

    /** Clears any visible error (useful on view initialisation). */
    public void clearError() {
        errorMessage.set("");
    }
}
