package com.entidaddesconocida.monte_erp.infrastructure.config;

import com.entidaddesconocida.monte_erp.domain.model.User;
import com.entidaddesconocida.monte_erp.domain.repository.UserRepository;
import com.entidaddesconocida.monte_erp.infrastructure.MySqlUserRepository;
import com.entidaddesconocida.monte_erp.security.PasswordHasher;

import java.sql.SQLException;

/**
 * Minimum data the application needs in order to sign in.
 *
 * <p>It is done in Java (rather than in a SQL script) so the hashes are produced
 * by the same {@link PasswordHasher} that later verifies them: a hand written
 * hash could not be generated without the salt and could end up out of sync.</p>
 *
 * <p>It is idempotent: a user is only inserted when its login name does not
 * exist yet, so starting the application several times neither duplicates rows
 * nor overwrites passwords that an administrator may have changed.</p>
 */
public final class DataSeeder {

    /** Development credentials, documented in the project plan. */
    public static final String ADMIN_USERNAME = "admin";
    public static final String ADMIN_PASSWORD = "admin123";
    public static final String EMPLOYEE_USERNAME = "employee";
    public static final String EMPLOYEE_PASSWORD = "employee123";

    private static final UserRepository USER_REPOSITORY = new MySqlUserRepository();

    private DataSeeder() {
        // Utility class: never instantiated.
    }

    /**
     * Creates whichever initial users are missing.
     *
     * @return the number of users created
     */
    public static int seed() throws SQLException {
        int created = 0;
        // Display names are user facing, so they are written in Spanish.
        created += createIfNotExists("Administrador", ADMIN_USERNAME, ADMIN_PASSWORD, User.ROLE_ADMIN);
        created += createIfNotExists("Empleado de mostrador", EMPLOYEE_USERNAME, EMPLOYEE_PASSWORD, User.ROLE_EMPLOYEE);
        return created;
    }

    private static int createIfNotExists(String name, String username, String password, String role)
            throws SQLException {
        if (USER_REPOSITORY.findByUsername(username) != null) {
            return 0;
        }
        User user = new User(name, username, PasswordHasher.hash(password), role, true);
        USER_REPOSITORY.insert(user);
        return 1;
    }
}
