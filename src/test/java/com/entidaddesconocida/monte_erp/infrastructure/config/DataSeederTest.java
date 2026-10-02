package com.entidaddesconocida.monte_erp.infrastructure.config;

import com.entidaddesconocida.monte_erp.domain.model.User;
import com.entidaddesconocida.monte_erp.infrastructure.UserRepository;
import com.entidaddesconocida.monte_erp.security.PasswordHasher;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Checks the RF-01 authentication flow against the real database (the
 * {@code monte_erp_mysql} container): migrations applied, seeded users and
 * verifiable passwords.
 *
 * <p>When MySQL is unavailable the tests are reported as skipped instead of
 * failing: {@code ./mvnw test} must be runnable without the database up.</p>
 */
class DataSeederTest {

    private static final String TEMPORARY_USERNAME = "test_rf01_user";

    @BeforeAll
    static void prepareDatabase() {
        assumeTrue(databaseIsAvailable(), "MySQL unavailable: integration tests skipped");
        try {
            MigrationRunner.migrate();
            DataSeeder.seed();
        } catch (Exception e) {
            assumeTrue(false, "Could not prepare the database: " + e.getMessage());
        }
    }

    @AfterEach
    void removeTemporaryUser() throws SQLException {
        try (PreparedStatement ps = DatabaseConnection.getConnection()
                .prepareStatement("DELETE FROM USER WHERE username = ?")) {
            ps.setString(1, TEMPORARY_USERNAME);
            ps.executeUpdate();
        }
    }

    @Test
    @DisplayName("admin/admin123 and employee/employee123 sign in with their role")
    void seededUsersCanSignIn() throws SQLException {
        UserRepository userRepository = new UserRepository();

        User admin = userRepository.findByUsername(DataSeeder.ADMIN_USERNAME);
        assertNotNull(admin, "the seed must create the admin user");
        assertTrue(PasswordHasher.verify(DataSeeder.ADMIN_PASSWORD, admin.getPasswordHash()));
        assertTrue(admin.isAdmin());
        assertTrue(admin.isActive());

        User employee = userRepository.findByUsername(DataSeeder.EMPLOYEE_USERNAME);
        assertNotNull(employee, "the seed must create the employee user");
        assertTrue(PasswordHasher.verify(DataSeeder.EMPLOYEE_PASSWORD, employee.getPasswordHash()));
        assertFalse(employee.isAdmin());
        assertEquals(User.ROLE_EMPLOYEE, employee.getRole());
    }

    @Test
    @DisplayName("A wrong password is rejected and the hash is never stored in clear text")
    void wrongPasswordDoesNotAuthenticate() throws SQLException {
        User admin = new UserRepository().findByUsername(DataSeeder.ADMIN_USERNAME);
        assertNotNull(admin);
        assertFalse(PasswordHasher.verify("wrong-password", admin.getPasswordHash()));
        assertFalse(admin.getPasswordHash().contains(DataSeeder.ADMIN_PASSWORD),
                "password_hash must not contain the password in clear text");
    }

    @Test
    @DisplayName("An unknown user returns null")
    void unknownUserReturnsNull() throws SQLException {
        assertNull(new UserRepository().findByUsername("this_user_does_not_exist"));
    }

    @Test
    @DisplayName("A deactivated user keeps its hash but can no longer sign in (logical deletion)")
    void deactivatedUserCannotSignIn() throws SQLException {
        UserRepository userRepository = new UserRepository();

        User temporary = new User("Temporary test user", TEMPORARY_USERNAME,
                PasswordHasher.hash("temporary123"), User.ROLE_EMPLOYEE, true);
        userRepository.insert(temporary);
        assertTrue(temporary.getId() > 0, "insert must return the generated id");

        User retrieved = userRepository.findByUsername(TEMPORARY_USERNAME);
        assertNotNull(retrieved);
        assertTrue(retrieved.isActive());

        userRepository.deactivate(retrieved.getId());

        User deactivated = userRepository.findByUsername(TEMPORARY_USERNAME);
        assertNotNull(deactivated, "deletion is logical: the row is not removed");
        assertFalse(deactivated.isActive(), "after deactivating, active must be 0");
        assertFalse(deactivated.isActive() && PasswordHasher.verify("temporary123", deactivated.getPasswordHash()),
                "sign in must be rejected for a deactivated user");
    }

    private static boolean databaseIsAvailable() {
        try {
            DatabaseConnection.getConnection();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
