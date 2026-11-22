package com.entidaddesconocida.monte_erp.viewmodel;

import com.entidaddesconocida.monte_erp.app.BackgroundTasks;
import com.entidaddesconocida.monte_erp.app.SessionValidator;
import com.entidaddesconocida.monte_erp.domain.model.Supplier;
import com.entidaddesconocida.monte_erp.domain.repository.SupplierRepository;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.collections.ObservableList;

/**
 * ViewModel of the supplier management screen (RF-05 / CU-05).
 *
 * <p>Holds the supplier list, the search and the edition form. Suppliers are
 * disabled logically, never deleted physically (decision D1), because
 * {@code PURCHASE} references {@code SUPPLIER} with
 * {@code ON DELETE RESTRICT}. A supplier given up (inactive) cannot be assigned
 * to a purchase but is still shown and searchable.</p>
 *
 * <p>The field rules, the duplicate-name check and the CRUD skeleton are shared
 * with customers through {@link ContactFormViewModel}; only the repository, the
 * entity factory and the texts that name the supplier live here.</p>
 */
public class SuppliersViewModel extends ContactFormViewModel<Supplier> {

    /** Label of the destructive toggle action (logically delete the selected supplier). */
    public static final String TOGGLE_DEACTIVATE_LABEL = "Dar de baja proveedor";

    /** Label of the recovery toggle action (activate the selected supplier). */
    public static final String TOGGLE_ACTIVATE_LABEL = "Reactivar proveedor";

    /** Labels and messages of the screen, handed to the CRUD template. */
    private static final CrudLabels<Supplier> CRUD_LABELS = new CrudLabels<>(
            TOGGLE_DEACTIVATE_LABEL,
            TOGGLE_ACTIVATE_LABEL,
            "Selecciona un proveedor de la tabla.",
            "El proveedor ya está activo.",
            "El proveedor ya está dado de baja.",
            supplier -> "Proveedor '" + supplier.getName() + "' dado de baja.",
            supplier -> "Proveedor '" + supplier.getName() + "' reactivado.",
            "Proveedor guardado correctamente.");

    private static final ContactLabels CONTACT_LABELS = new ContactLabels(
            "Introduce el nombre del proveedor.",
            name -> "El proveedor '" + name + "' ya existe.",
            "Proveedores",
            "Nuevo proveedor",
            "Editar proveedor");

    public SuppliersViewModel(SupplierRepository supplierRepository) {
        this(supplierRepository, null);
    }

    public SuppliersViewModel(SupplierRepository supplierRepository, SessionValidator sessionValidator) {
        this(supplierRepository, sessionValidator, BackgroundTasks.synchronous());
    }

    public SuppliersViewModel(SupplierRepository supplierRepository, SessionValidator sessionValidator,
                        BackgroundTasks backgroundTasks) {
        super(supplierRepository, CONTACT_LABELS, CRUD_LABELS, sessionValidator, backgroundTasks);
    }

    /** Master list currently loaded (active or inactive suppliers). */
    public ObservableList<Supplier> suppliers() {
        return contacts();
    }

    /** Currently selected supplier, or {@code null} when none is selected. */
    public ObjectProperty<Supplier> selectedSupplierProperty() {
        return selectedContactProperty();
    }

    /** Footer text with the number of suppliers currently shown. */
    public ReadOnlyStringProperty supplierCountProperty() {
        return contactCountProperty();
    }

    /** Clears the form so a brand new supplier can be typed in. */
    public void newSupplier() {
        newContact();
    }

    @Override
    protected Supplier createContact(String name, String phone, String email, String address) {
        // A brand new supplier is born active (see Supplier(String, String, String, String)).
        return new Supplier(name, phone, email, address);
    }

    @Override
    protected Supplier copyContact(Supplier source, String name, String phone, String email, String address) {
        return new Supplier(source.getId(), name, phone, email, address, source.isActive());
    }
}
