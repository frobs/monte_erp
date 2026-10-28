package com.entidaddesconocida.monte_erp.app;

import com.entidaddesconocida.monte_erp.domain.model.User;
import com.entidaddesconocida.monte_erp.security.PasswordHasher;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for the {@link AppShell} navigation guards (RF-02).
 *
 * <p>No JavaFX toolkit: the admin check and the session check throw before
 * {@code FluentViewLoader} or {@code SceneNavigator} are reached.</p>
 */
class AppShellTest {

    @Test
    @DisplayName("showUsers sin sesión falla con IllegalStateException")
    void showUsersWithoutSessionFails() {
        AppShell shell = new AppShell(new SessionState());

        IllegalStateException ex = assertThrows(IllegalStateException.class, shell::showUsers);

        assertEquals("No hay sesión iniciada", ex.getMessage());
    }

    @Test
    @DisplayName("showUsers con un empleado falla con IllegalStateException")
    void showUsersAsEmployeeFails() {
        SessionState session = new SessionState();
        session.signIn(new User("Bea", "bea", PasswordHasher.hash("bea123"), User.ROLE_EMPLOYEE, true));
        AppShell shell = new AppShell(session);

        IllegalStateException ex = assertThrows(IllegalStateException.class, shell::showUsers);

        assertEquals("La gestión de usuarios es exclusiva del administrador", ex.getMessage());
    }

    @Test
    @DisplayName("showMain sin sesión falla con IllegalStateException antes de cargar la vista")
    void showMainWithoutSessionFails() {
        AppShell shell = new AppShell(new SessionState());

        IllegalStateException ex = assertThrows(IllegalStateException.class, shell::showMain);

        assertEquals("No hay sesión iniciada", ex.getMessage());
    }

    @Test
    @DisplayName("showProducts sin sesión falla con IllegalStateException antes de cargar la vista")
    void showProductsWithoutSessionFails() {
        AppShell shell = new AppShell(new SessionState());

        IllegalStateException ex = assertThrows(IllegalStateException.class, shell::showProducts);

        assertEquals("No hay sesión iniciada", ex.getMessage());
    }

    @Test
    @DisplayName("showCategories sin sesión falla con IllegalStateException antes de cargar la vista")
    void showCategoriesWithoutSessionFails() {
        AppShell shell = new AppShell(new SessionState());

        IllegalStateException ex = assertThrows(IllegalStateException.class, shell::showCategories);

        assertEquals("No hay sesión iniciada", ex.getMessage());
    }

    @Test
    @DisplayName("showCustomers sin sesión falla con IllegalStateException antes de cargar la vista")
    void showCustomersWithoutSessionFails() {
        AppShell shell = new AppShell(new SessionState());

        IllegalStateException ex = assertThrows(IllegalStateException.class, shell::showCustomers);

        assertEquals("No hay sesión iniciada", ex.getMessage());
    }

    @Test
    @DisplayName("showSalesOrders sin sesión falla con IllegalStateException antes de cargar la vista")
    void showSalesOrdersWithoutSessionFails() {
        AppShell shell = new AppShell(new SessionState());

        IllegalStateException ex = assertThrows(IllegalStateException.class, shell::showSalesOrders);

        assertEquals("No hay sesión iniciada", ex.getMessage());
    }

    @Test
    @DisplayName("showReports sin sesión falla con IllegalStateException antes de cargar la vista")
    void showReportsWithoutSessionFails() {
        AppShell shell = new AppShell(new SessionState());

        IllegalStateException ex = assertThrows(IllegalStateException.class, shell::showReports);

        assertEquals("No hay sesión iniciada", ex.getMessage());
    }

    @Test
    @DisplayName("isOnboarding es false por defecto")
    void onboardingIsFalseByDefault() {
        assertFalse(new AppShell(new SessionState()).isOnboarding());
    }

    @Test
    @DisplayName("exit sin stage no falla")
    void exitWithoutStageDoesNothing() {
        new AppShell(new SessionState()).exit();
    }
}
