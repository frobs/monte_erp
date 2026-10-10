package com.entidaddesconocida.monte_erp.domain.model;

/**
 * Supplier (the {@code SUPPLIER} table of the E-R diagram).
 *
 * <p>RF-03 only reads suppliers to fill the product form combo box; the full
 * CRUD belongs to RF-05.</p>
 */
public class Supplier {

    private int id;
    private String name;
    private String phone;
    private String email;

    public Supplier() {
    }

    public Supplier(String name, String phone, String email) {
        this.name = name;
        this.phone = phone;
        this.email = email;
    }

    public Supplier(int id, String name, String phone, String email) {
        this.id = id;
        this.name = name;
        this.phone = phone;
        this.email = email;
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

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    /**
     * Name, so a {@code ComboBox} renders the supplier without an extra cell
     * factory. User facing text; same note as in {@link Category#toString()}.
     */
    @Override
    public String toString() {
        return name;
    }
}
