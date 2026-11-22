package com.entidaddesconocida.monte_erp.domain.model;

/**
 * Customer (the {@code CUSTOMER} table of the E-R diagram).
 *
 * <p>RF-06 adds the full CRUD and the logical deletion ({@code active}), exactly
 * like {@link User}, {@link Product}, {@link Category} and {@link Supplier}.
 * RF-07 will read customers to select the customer of a sales order.</p>
 */
public class Customer implements Contact {

    /** User facing label of an active customer. */
    public static final String ACTIVE_LABEL = "Activo";

    /** User facing label of a logically deleted customer. */
    public static final String INACTIVE_LABEL = "Inactivo";

    private int id;
    private String name;
    private String phone;
    private String email;
    private String address;
    private boolean active;

    public Customer() {
    }

    /**
     * Creates an active customer: a brand new customer is always enabled, so the
     * Java default ({@code false}) is deliberately overridden here.
     */
    public Customer(String name, String phone, String email, String address) {
        this.name = name;
        this.phone = phone;
        this.email = email;
        this.address = address;
        this.active = true;
    }

    public Customer(int id, String name, String phone, String email, String address) {
        this(id, name, phone, email, address, true);
    }

    public Customer(int id, String name, String phone, String email, String address, boolean active) {
        this.id = id;
        this.name = name;
        this.phone = phone;
        this.email = email;
        this.address = address;
        this.active = active;
    }

    @Override
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

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
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
     * Name, so a {@code ComboBox} renders the customer without an extra cell
     * factory. User facing text; same note as in {@link Category#toString()}.
     */
    @Override
    public String toString() {
        return name;
    }
}
