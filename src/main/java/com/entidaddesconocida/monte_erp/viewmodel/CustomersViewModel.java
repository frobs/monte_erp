package com.entidaddesconocida.monte_erp.viewmodel;

import com.entidaddesconocida.monte_erp.app.BackgroundTasks;
import com.entidaddesconocida.monte_erp.app.SessionValidator;
import com.entidaddesconocida.monte_erp.domain.model.Customer;
import com.entidaddesconocida.monte_erp.domain.repository.CustomerRepository;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.collections.ObservableList;

/**
 * ViewModel of the customer management screen (RF-06 / CU-06).
 *
 * <p>Holds the customer list, the search and the edition form. Customers are
 * disabled logically, never deleted physically (decision D1), because
 * {@code SALES_ORDER} references {@code CUSTOMER} with
 * {@code ON DELETE RESTRICT}. A customer given up (inactive) cannot be assigned
 * to a sales order but is still shown and searchable.</p>
 *
 * <p>The field rules, the duplicate-name check and the CRUD skeleton are shared
 * with suppliers through {@link ContactFormViewModel}; only the repository, the
 * entity factory and the texts that name the customer live here.</p>
 */
public class CustomersViewModel extends ContactFormViewModel<Customer> {

    /** Label of the destructive toggle action (logically delete the selected customer). */
    public static final String TOGGLE_DEACTIVATE_LABEL = "Dar de baja cliente";

    /** Label of the recovery toggle action (activate the selected customer). */
    public static final String TOGGLE_ACTIVATE_LABEL = "Reactivar cliente";

    /** Labels and messages of the screen, handed to the CRUD template. */
    private static final CrudLabels<Customer> CRUD_LABELS = new CrudLabels<>(
            TOGGLE_DEACTIVATE_LABEL,
            TOGGLE_ACTIVATE_LABEL,
            "Selecciona un cliente de la tabla.",
            "El cliente ya está activo.",
            "El cliente ya está dado de baja.",
            customer -> "Cliente '" + customer.getName() + "' dado de baja.",
            customer -> "Cliente '" + customer.getName() + "' reactivado.",
            "Cliente guardado correctamente.");

    private static final ContactLabels CONTACT_LABELS = new ContactLabels(
            "Introduce el nombre del cliente.",
            name -> "El cliente '" + name + "' ya existe.",
            "Clientes",
            "Nuevo cliente",
            "Editar cliente");

    public CustomersViewModel(CustomerRepository customerRepository) {
        this(customerRepository, null);
    }

    public CustomersViewModel(CustomerRepository customerRepository, SessionValidator sessionValidator) {
        this(customerRepository, sessionValidator, BackgroundTasks.synchronous());
    }

    public CustomersViewModel(CustomerRepository customerRepository, SessionValidator sessionValidator,
                        BackgroundTasks backgroundTasks) {
        super(customerRepository, CONTACT_LABELS, CRUD_LABELS, sessionValidator, backgroundTasks);
    }

    /** Master list currently loaded (active or inactive customers). */
    public ObservableList<Customer> customers() {
        return contacts();
    }

    /** Currently selected customer, or {@code null} when none is selected. */
    public ObjectProperty<Customer> selectedCustomerProperty() {
        return selectedContactProperty();
    }

    /** Footer text with the number of customers currently shown. */
    public ReadOnlyStringProperty customerCountProperty() {
        return contactCountProperty();
    }

    /** Clears the form so a brand new customer can be typed in. */
    public void newCustomer() {
        newContact();
    }

    @Override
    protected Customer createContact(String name, String phone, String email, String address) {
        // A brand new customer is born active (see Customer(String, String, String, String)).
        return new Customer(name, phone, email, address);
    }

    @Override
    protected Customer copyContact(Customer source, String name, String phone, String email, String address) {
        return new Customer(source.getId(), name, phone, email, address, source.isActive());
    }
}
