package com.entidaddesconocida.monte_erp.domain.model;

/**
 * Product category (the {@code CATEGORY} table of the E-R diagram).
 *
 * <p>RF-03 only reads categories to fill the product form combo box; the full
 * CRUD belongs to RF-04.</p>
 */
public class Category {

    private int id;
    private String name;
    private String description;

    public Category() {
    }

    public Category(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public Category(int id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
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

    /**
     * Name, so a {@code ComboBox} renders the category without an extra cell
     * factory. User facing text, therefore Spanish is allowed here as in every
     * other label of the interface (the name comes from the database anyway).
     */
    @Override
    public String toString() {
        return name;
    }
}
