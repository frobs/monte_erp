package com.entidaddesconocida.monte_erp.app;

import com.entidaddesconocida.monte_erp.domain.model.User;
import com.entidaddesconocida.monte_erp.domain.repository.UserRepository;

import java.sql.SQLException;

/**
 * Revalidates a signed-in user against persisted account status and permissions.
 *
 * <p>The session holds a {@link SessionUser} snapshot, so refreshing means
 * <strong>replacing</strong> it: the copy read from the repository never carries
 * its password hash into the session, and no screen mutates shared state. Every
 * action of a ViewModel calls {@link #refresh()} first, so a demotion or a
 * deactivation applies to the next write it attempts.</p>
 */
public final class SessionValidator {

    private final SessionState session;
    private final UserRepository userRepository;

    public SessionValidator(SessionState session, UserRepository userRepository) {
        this.session = session;
        this.userRepository = userRepository;
    }

    /**
     * Refreshes the in-memory user from the repository, or signs out an absent
     * or deactivated account.
     *
     * @return {@code true} when the current account still exists and is active
     */
    public boolean refresh() throws SQLException {
        SessionUser current = session.currentUser();
        if (current == null) {
            return false;
        }

        User persisted = userRepository.findByUsername(current.username());
        if (persisted == null || !persisted.isActive()) {
            session.signOut();
            return false;
        }

        // New snapshot: the previous one is immutable, so the screens that read
        // it through currentUser() observe the refreshed role on their next check.
        session.refresh(persisted);
        return true;
    }

    /**
     * Live snapshot of the signed-in user, or {@code null} without a session.
     * Used by the rules that depend on the <em>current</em> role instead of the
     * one the screen was built with.
     */
    public SessionUser currentUser() {
        return session.currentUser();
    }

    /**
     * Updates the snapshot with values already persisted for the same account
     * (an administrator editing their own name or username), without a query.
     * A different user is ignored.
     */
    public void synchronize(User persisted) {
        SessionUser current = session.currentUser();
        if (current != null && persisted != null && current.id() == persisted.getId()) {
            session.refresh(persisted);
        }
    }
}
