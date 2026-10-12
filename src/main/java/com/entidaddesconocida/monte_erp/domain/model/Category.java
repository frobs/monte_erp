package com.entidaddesconocida.monte_erp.domain.model;

/**
 * Product category (the {@code CATEGORY} table of the E-R diagram).
 *
 * <p>RF-03 only reads categories to fill the product form combo box; RF-04 adds
 * the full CRUD and the logical deletion ({@code active}), exactly like
 * {@link User} and {@link Product}.</p>
 */
public class Category {

    /** User facing label of an active category. */
    public static final String ACTIVE_LABEL = "Activa";

    /** User facing label of a logically deleted category. */
    public static final String INACTIVE_LABEL = "Inactiva";

    private int id;
    private String name;
    private String description;
    private boolean active;

    public Category() {
    }

    /**
     * Creates an active category: a brand new category is always enabled, so
     * the Java default ({@code false}) is deliberately overridden here.
     */
    public Category(String name, String description) {
        this.name = name;
        this.description = description;
        this.active = true;
    }

    public Category(int id, String name, String description) {
        this(id, name, description, true);
    }

    public Category(int id, String name, String description, boolean active) {
        this.id = id;
        this.name = name;
        this.description = description;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    /** Human readable status label, for the user interface. */
    public String getStatusLabel() {
        return active ? ACTIVE_LABEL : INACTIVE_LABEL;
    }

    /**
     * Name, so a {@code ComboBox} renders the category without an extra cell
     * factory. User facing text, therefore Spanish is allowed here as in every
     * other label of the interface (the name comes from the database anyway).
     *
     * <p>The {@code "... (dada de baja)"} suffix is added by the View cell
     * factory when the category is inactive; this method must not change its
     * format because the product combos rely on it.</p>
     */
    @Override
    public String toString() {
        return name;
    }
}
