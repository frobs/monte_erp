package com.entidaddesconocida.monte_erp.app;

import com.entidaddesconocida.monte_erp.domain.model.User;

/**
 * Holds the currently signed-in user.
 *
 * <p>It exists to break the cycle {@code AppShell ⇄ AppContext} (both depend on
 * the session, which is created first) and to make the rule "no main screen
 * without a signed-in session" testable without JavaFX.</p>
 *
 * <p>The state is a {@link SessionUser} snapshot, never the {@link User} entity:
 * the session carries no credentials and the screens cannot mutate it. It is
 * refreshed by {@link SessionValidator} on every action.</p>
 */
public final class SessionState {

    private SessionUser currentUser;

    /** Signed-in user, or {@code null} when there is no session. */
    public SessionUser currentUser() {
        return currentUser;
    }

    /**
     * @throws IllegalStateException if no session has been started
     */
    public SessionUser requireCurrentUser() {
        if (currentUser == null) {
            throw new IllegalStateException("No hay sesión iniciada");
        }
        return currentUser;
    }

    /**
     * Starts the session for {@code user}. Only the session relevant fields are
     * kept (see {@link SessionUser}); the password hash stays behind.
     */
    public void signIn(User user) {
        this.currentUser = SessionUser.of(user);
    }

    /**
     * Replaces the snapshot with the values just read from the database for the
     * same account. Package-private: {@link SessionValidator} is the only class
     * allowed to refresh the session.
     */
    void refresh(User persisted) {
        if (currentUser != null && persisted != null) {
            this.currentUser = SessionUser.of(persisted);
        }
    }

    public void signOut() {
        this.currentUser = null;
    }
}
