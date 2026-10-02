package com.entidaddesconocida.monte_erp.app;

import com.entidaddesconocida.monte_erp.domain.model.User;

/**
 * Holds the currently signed-in user.
 *
 * <p>It exists to break the cycle {@code AppShell ⇄ AppContext} (both depend on
 * the session, which is created first) and to make the rule "no main screen
 * without a signed-in session" testable without JavaFX.</p>
 */
public final class SessionState {

    private User currentUser;

    public User currentUser() {
        return currentUser;
    }

    /**
     * @throws IllegalStateException if no session has been started
     */
    public User requireCurrentUser() {
        if (currentUser == null) {
            throw new IllegalStateException("No hay sesión iniciada");
        }
        return currentUser;
    }

    public void signIn(User user) {
        this.currentUser = user;
    }

    public void signOut() {
        this.currentUser = null;
    }
}
