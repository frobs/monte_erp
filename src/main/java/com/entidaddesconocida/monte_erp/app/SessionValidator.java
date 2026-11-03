package com.entidaddesconocida.monte_erp.app;

import com.entidaddesconocida.monte_erp.domain.model.User;
import com.entidaddesconocida.monte_erp.domain.repository.UserRepository;

import java.sql.SQLException;

/** Revalidates a signed-in user against persisted account status and permissions. */
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
        User current = session.currentUser();
        if (current == null) {
            return false;
        }

        User persisted = userRepository.findByUsername(current.getUsername());
        if (persisted == null || !persisted.isActive()) {
            session.signOut();
            return false;
        }

        // Keep the same object so already-loaded ViewModels observe role changes.
        current.setName(persisted.getName());
        current.setUsername(persisted.getUsername());
        current.setPasswordHash(persisted.getPasswordHash());
        current.setRole(persisted.getRole());
        current.setActive(true);
        return true;
    }
}
