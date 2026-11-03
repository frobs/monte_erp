package com.entidaddesconocida.monte_erp.infrastructure.config;

import com.entidaddesconocida.monte_erp.domain.model.ConnectionSettings;
import com.entidaddesconocida.monte_erp.domain.model.User;
import com.entidaddesconocida.monte_erp.domain.repository.UserRepository;
import com.entidaddesconocida.monte_erp.domain.service.SetupException;
import com.entidaddesconocida.monte_erp.domain.service.SetupService;
import com.entidaddesconocida.monte_erp.security.PasswordHasher;

import java.io.IOException;
import java.sql.SQLException;

/**
 * Infrastructure implementation of {@link SetupService}.
 *
 * <p>It composes the pieces the domain layer must not know about: the
 * properties file and the keyring ({@link DatabaseConfig}), the shared JDBC
 * connection ({@link DatabaseConnection}), the migration engine
 * ({@link MigrationRunner}) and the {@link UserRepository} for the
 * administrator user.</p>
 */
public final class DatabaseSetupService implements SetupService {

    private final DatabaseConfig config;
    private final UserRepository userRepository;

    public DatabaseSetupService(DatabaseConfig config, UserRepository userRepository) {
        this.config = config;
        this.userRepository = userRepository;
    }

    @Override
    public ConnectionSettings load() {
        return config.load();
    }

    @Override
    public void testConnection(ConnectionSettings settings) throws SetupException {
        try {
            DatabaseConnection.testConnection(settings.url(), settings.user(), settings.password());
        } catch (SQLException e) {
            throw new SetupException(rootMessage(e), e);
        }
    }

    @Override
    public void save(ConnectionSettings settings) throws SetupException {
        persist(settings);
    }

    @Override
    public void provision(ConnectionSettings settings, String adminName, String adminUsername,
                          String adminPassword) throws SetupException {
        persist(settings);
        try {
            MigrationRunner.migrate();
        } catch (SQLException | IOException e) {
            throw new SetupException(rootMessage(e), e);
        }
        createAdminUser(adminName, adminUsername, adminPassword);
    }

    @Override
    public boolean hasAdminUser() throws SetupException {
        try {
            return userRepository.findAll().stream().anyMatch(User::isAdmin);
        } catch (SQLException e) {
            throw new SetupException(rootMessage(e), e);
        }
    }

    private void persist(ConnectionSettings settings) throws SetupException {
        try {
            config.save(settings);
        } catch (IOException | CredentialStoreException | IllegalArgumentException e) {
            throw new SetupException(rootMessage(e), e);
        }
        DatabaseConnection.configure(settings.url(), settings.user(), settings.password());
    }

    private void createAdminUser(String name, String username, String password) throws SetupException {
        try {
            User existing = userRepository.findByUsername(username);
            if (existing != null) {
                if (existing.isAdmin()) {
                    return; // idempotent: onboarding can be run several times.
                }
                throw new SetupException("El nombre de usuario '" + username
                        + "' ya existe y no es administrador.");
            }
            User admin = new User(name, username,
                    PasswordHasher.hash(password), User.ROLE_ADMIN, true);
            userRepository.insert(admin);
        } catch (SQLException e) {
            throw new SetupException(rootMessage(e), e);
        }
    }

    private static String rootMessage(Throwable t) {
        Throwable current = t;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        if (message == null || message.isBlank()) {
            return current.getClass().getSimpleName();
        }
        // Avoid exposing file paths, SQL fragments or network details in user-facing messages.
        String sanitized = message
                .replaceAll("([A-Za-z]:)?[/\\\\][^\\s]*", "[ruta]")
                .replaceAll("\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}(:\\d+)?\\b", "[red]");
        return sanitized.length() > 200 ? sanitized.substring(0, 200) + "..." : sanitized;
    }
}
