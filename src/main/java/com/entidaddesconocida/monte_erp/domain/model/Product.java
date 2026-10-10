package com.entidaddesconocida.monte_erp.domain.model;

import java.math.BigDecimal;

/**
 * Product (the {@code PRODUCT} table of the E-R diagram).
 *
 * <p>The stock is <strong>not</strong> an attribute of the product: it is
 * computed by adding the {@code availableQuantity} of its lots ({@code Stock}),
 * which belongs to RF-09/RF-10. That is why this entity does not expose
 * {@code availableStock()} / {@code isBelowMinimum()} yet.</p>
 *
 * <p>The supplier is <strong>not</strong> an attribute either: the relation goes
 * through the purchase ({@code PRODUCT} → {@code STOCK} → {@code PURCHASE} →
 * {@code SUPPLIER}), so the same product can be bought from several suppliers
 * over time.</p>
 *
 * <p>Conscious deviation from the class diagram: the price is a
 * {@link BigDecimal} (the column is {@code DECIMAL(10, 2)}); using
 * {@code double} for money is a classic mistake.</p>
 */
public class Product {

    /** User facing label of an active product. */
    public static final String ACTIVE_LABEL = "Activo";

    /** User facing label of a logically deleted product. */
    public static final String INACTIVE_LABEL = "Inactivo";

    private int id;
    private String name;
    private String sku;
    private String description;
    private BigDecimal price;
    private int minStock;
    private Category category;
    private boolean active;

    public Product() {
    }

    public Product(String name, String sku, String description, BigDecimal price, int minStock,
                   Category category, boolean active) {
        this.name = name;
        this.sku = sku;
        this.description = description;
        this.price = price;
        this.minStock = minStock;
        this.category = category;
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

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public int getMinStock() {
        return minStock;
    }

    public void setMinStock(int minStock) {
        this.minStock = minStock;
    }

    /** Category the product belongs to (the {@code category_id} foreign key). */
    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
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

    @Override
    public String toString() {
        return "Product{id=" + id + ", sku='" + sku + "', name='" + name
                + "', active=" + active + '}';
    }
}
