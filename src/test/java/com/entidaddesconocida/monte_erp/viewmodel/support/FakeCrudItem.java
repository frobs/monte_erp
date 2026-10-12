package com.entidaddesconocida.monte_erp.viewmodel.support;

/**
 * Minimal entity used by {@code CrudViewModelTest} to exercise the generic CRUD
 * template without a real domain entity, a database or the JavaFX toolkit.
 */
public class FakeCrudItem {

    private int id;
    private String name;
    private boolean active;

    public FakeCrudItem(String name) {
        this(name, true);
    }

    public FakeCrudItem(String name, boolean active) {
        this.name = name;
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

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
