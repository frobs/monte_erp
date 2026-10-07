package com.entidaddesconocida.monte_erp.domain.model;

/**
 * System user (the {@code USER} table of the E-R diagram).
 *
 * <p>The password is never stored in clear text: {@link #passwordHash} holds the
 * PBKDF2 hash produced by {@code security.PasswordHasher}. Users are disabled
 * logically, through {@link #active}.</p>
 */
public class User {

    /** Role with administration permissions. */
    public static final String ROLE_ADMIN = "admin";

    /** Employee role (basic permissions). */
    public static final String ROLE_EMPLOYEE = "employee";

    /** User facing label of {@link #ROLE_ADMIN} (single source of truth). */
    public static final String ROLE_ADMIN_LABEL = "Administrador";

    /** User facing label of {@link #ROLE_EMPLOYEE} (single source of truth). */
    public static final String ROLE_EMPLOYEE_LABEL = "Empleado";

    /** User facing label of an active account. */
    public static final String ACTIVE_LABEL = "Activo";

    /** User facing label of a disabled account. */
    public static final String INACTIVE_LABEL = "Inactivo";

    private int id;
    private String name;
    private String username;
    private String passwordHash;
    private String role;
    private boolean active;

    public User() {
    }

    public User(String name, String username, String passwordHash, String role, boolean active) {
        this.name = name;
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.active = active;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    /** {@code true} when the user holds the administrator role. */
    public boolean isAdmin() {
        return ROLE_ADMIN.equals(role);
    }

    /** Human readable role label, for the user interface (Spanish, as every other user facing text). */
    public String getRoleLabel() {
        return isAdmin() ? ROLE_ADMIN_LABEL : ROLE_EMPLOYEE_LABEL;
    }

    /** Human readable account status label, for the user interface. */
    public String getStatusLabel() {
        return active ? ACTIVE_LABEL : INACTIVE_LABEL;
    }

    @Override
    public String toString() {
        return "User{id=" + id + ", username='" + username + "', role='" + role + "', active=" + active + '}';
    }
}
