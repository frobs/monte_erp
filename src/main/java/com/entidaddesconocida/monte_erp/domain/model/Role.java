package com.entidaddesconocida.monte_erp.domain.model;

/**
 * Role of a system user, matching the {@code USER.role} ENUM of the database
 * (RF-01 / RF-02).
 *
 * <p>Keeping the database value ({@link #dbValue()}) and the label shown to the
 * user ({@link #label()}) in the enum makes an unknown role impossible to
 * construct and removes the parallel {@code String} constants and their
 * {@code isAdmin()} string comparison.</p>
 */
public enum Role {

    /** Role with administration permissions. */
    ADMIN("admin", "Administrador"),

    /** Employee role (basic permissions). */
    EMPLOYEE("employee", "Empleado");

    private final String dbValue;
    private final String label;

    Role(String dbValue, String label) {
        this.dbValue = dbValue;
        this.label = label;
    }

    /** Value stored in the {@code ENUM} column. */
    public String dbValue() {
        return dbValue;
    }

    /** Human readable label, for the user interface (Spanish). */
    public String label() {
        return label;
    }

    /**
     * Parses the value stored in the database.
     *
     * @throws IllegalArgumentException when the value is not one of the ENUM
     *                                  values, so a corrupted row fails loudly
     */
    public static Role fromDb(String value) {
        for (Role role : values()) {
            if (role.dbValue.equals(value)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Rol desconocido: " + value);
    }
}
