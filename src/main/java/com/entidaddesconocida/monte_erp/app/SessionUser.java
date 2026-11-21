package com.entidaddesconocida.monte_erp.app;

import com.entidaddesconocida.monte_erp.domain.model.Role;
import com.entidaddesconocida.monte_erp.domain.model.User;

/**
 * Immutable, <strong>credential free</strong> snapshot of the signed-in user.
 *
 * <p>The session must never hold the password hash. It only needs what the
 * screens display and check (identity, role and account state), so the
 * {@code SessionState} stores this record instead of the {@link User} entity
 * returned by the repository: the hash stays in the login flow and is never
 * reachable from a ViewModel. Being a record, it also cannot be mutated by a
 * screen (the top bar used to depend on that shared mutation).</p>
 *
 * <p>{@link SessionValidator} replaces the snapshot with a fresh one on every
 * action, so a role change or a deactivation applies immediately.</p>
 */
public record SessionUser(int id, String name, String username, Role role, boolean active) {

    /**
     * Copies the session relevant fields of a persisted user, dropping the
     * password hash.
     *
     * @return the snapshot, or {@code null} when there is no user
     */
    public static SessionUser of(User user) {
        if (user == null) {
            return null;
        }
        return new SessionUser(user.getId(), user.getName(), user.getUsername(),
                user.getRole(), user.isActive());
    }

    /** {@code true} when the user holds the administrator role. */
    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    /** Human readable role label, for the user interface (Spanish). */
    public String roleLabel() {
        return isAdmin() ? Role.ADMIN.label() : Role.EMPLOYEE.label();
    }

    /**
     * Credential free {@link User} for the aggregates that record <em>who</em>
     * created them ({@code SALES_ORDER.user_id}, {@code PURCHASE.user_id}): the
     * repositories only read {@link User#getId()} from it, and the hash is
     * deliberately {@code null} so it can never be written back.
     */
    public User toEntityReference() {
        User reference = new User(name, username, null, role, active);
        reference.setId(id);
        return reference;
    }
}
