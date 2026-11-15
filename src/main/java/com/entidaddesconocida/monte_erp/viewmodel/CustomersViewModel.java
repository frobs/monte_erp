package com.entidaddesconocida.monte_erp.viewmodel;

import com.entidaddesconocida.monte_erp.app.BackgroundTasks;
import com.entidaddesconocida.monte_erp.app.SessionValidator;
import com.entidaddesconocida.monte_erp.domain.model.Customer;
import com.entidaddesconocida.monte_erp.domain.repository.CustomerRepository;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * ViewModel of the customer management screen (RF-06 / CU-06).
 *
 * <p>Holds the customer list, the search and the edition form. Customers are
 * disabled logically, never deleted physically (decision D1), because
 * {@code SALES_ORDER} references {@code CUSTOMER} with
 * {@code ON DELETE RESTRICT}. A customer given up (inactive) cannot be assigned
 * to a sales order but is still shown and searchable.</p>
 *
 * <p>The search travels to the database through
 * {@link CustomerRepository#findActive(String)} /
 * {@link CustomerRepository#findInactive(String)}, so the rows are filtered in
 * the SQL {@code WHERE} and not in memory.</p>
 *
 * <p>Only the customer specific parts live here: the field rules, the
 * duplicate-name rule and the labels. The CRUD skeleton (selection ↔ form,
 * messages, toggle, counter and the save/deactivate/activate templates) comes
 * from {@link FilterableCrudViewModel} (search + inactive toggle) and
 * {@link CrudViewModel}.</p>
 */
public class CustomersViewModel extends FilterableCrudViewModel<Customer> {

    /** Label of the destructive toggle action (logically delete the selected customer). */
    public static final String TOGGLE_DEACTIVATE_LABEL = "Dar de baja cliente";

    /** Label of the recovery toggle action (activate the selected customer). */
    public static final String TOGGLE_ACTIVATE_LABEL = "Reactivar cliente";

    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_PHONE_LENGTH = 20;
    private static final int MAX_EMAIL_LENGTH = 100;
    private static final int MAX_ADDRESS_LENGTH = 255;

    private final CustomerRepository customerRepository;

    private final StringProperty name = new SimpleStringProperty("");
    private final StringProperty phone = new SimpleStringProperty("");
    private final StringProperty email = new SimpleStringProperty("");
    private final StringProperty address = new SimpleStringProperty("");

    private final ReadOnlyStringWrapper nameError = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper phoneError = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper emailError = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper addressError = new ReadOnlyStringWrapper("");

    public CustomersViewModel(CustomerRepository customerRepository) {
        this(customerRepository, null);
    }

    public CustomersViewModel(CustomerRepository customerRepository, SessionValidator sessionValidator) {
        this(customerRepository, sessionValidator, BackgroundTasks.synchronous());
    }

    public CustomersViewModel(CustomerRepository customerRepository, SessionValidator sessionValidator,
                        BackgroundTasks backgroundTasks) {
        super(sessionValidator, backgroundTasks);
        this.customerRepository = customerRepository;
        bindFilter(searchText);
        watchFields(name, phone, email, address);
        // The phone is the only field validated while typing (decision D1).
        watchLiveField(phone, this::validatePhone, phoneError);
        updateCount();
    }

    /** Master list currently loaded (active or inactive customers). */
    public ObservableList<Customer> customers() {
        return items;
    }

    /** Filtered view bound to the table. */
    public FilteredList<Customer> filteredCustomers() {
        return filteredItems;
    }

    /** Currently selected customer, or {@code null} when none is selected. */
    public ObjectProperty<Customer> selectedCustomerProperty() {
        return selectedItem;
    }

    public StringProperty nameProperty() {
        return name;
    }

    /** Optional phone; a blank value is persisted as {@code NULL}. */
    public StringProperty phoneProperty() {
        return phone;
    }

    /** Optional email; a blank value is persisted as {@code NULL}. */
    public StringProperty emailProperty() {
        return email;
    }

    /** Optional address; a blank value is persisted as {@code NULL}. */
    public StringProperty addressProperty() {
        return address;
    }

    /** Footer text with the number of customers currently shown. */
    public ReadOnlyStringProperty customerCountProperty() {
        return itemCountProperty();
    }

    /** Empty when the name is valid; the message to show otherwise. */
    public ReadOnlyStringProperty nameErrorProperty() {
        return nameError.getReadOnlyProperty();
    }

    /** Empty when the phone is valid; the message to show otherwise. */
    public ReadOnlyStringProperty phoneErrorProperty() {
        return phoneError.getReadOnlyProperty();
    }

    /** Empty when the email is valid; the message to show otherwise. */
    public ReadOnlyStringProperty emailErrorProperty() {
        return emailError.getReadOnlyProperty();
    }

    /** Empty when the address is valid; the message to show otherwise. */
    public ReadOnlyStringProperty addressErrorProperty() {
        return addressError.getReadOnlyProperty();
    }

    /** Clears the form so a brand new customer can be typed in. */
    public void newCustomer() {
        newItem();
    }

    @Override
    protected Callable<List<Customer>> activeQuery() {
        // The criteria are captured here, on the UI thread.
        String search = searchText.get();
        return () -> customerRepository.findActive(search);
    }

    @Override
    protected Callable<List<Customer>> inactiveQuery() {
        String search = searchText.get();
        return () -> customerRepository.findInactive(search);
    }

    @Override
    protected void write() throws SQLException {
        String nameValue = trimmed(name.get());
        String phoneValue = blankToNull(trimmed(phone.get()));
        String emailValue = blankToNull(trimmed(email.get()));
        String addressValue = blankToNull(trimmed(address.get()));
        Customer selected = selectedItem.get();
        if (selected == null) {
            // A brand new customer is born active (see Customer(String, String, String, String)).
            customerRepository.insert(new Customer(nameValue, phoneValue, emailValue, addressValue));
        } else {
            // Persist a detached copy: a failed write must never leave the
            // rendered row with values that are not in the database.
            customerRepository.update(new Customer(selected.getId(), nameValue, phoneValue,
                    emailValue, addressValue, selected.isActive()));
        }
    }

    @Override
    protected boolean validateBusinessRules() throws SQLException {
        // An inactive customer still reserves its name (UNIQUE is global), so
        // the duplicate check spans both states.
        String nameValue = trimmed(name.get());
        Customer selected = selectedItem.get();
        Customer existing = customerRepository.findByName(nameValue);
        if (existing != null && (selected == null || existing.getId() != selected.getId())) {
            rejectDuplicateName(nameValue);
            return false;
        }
        return true;
    }

    @Override
    protected void handleIntegrityViolation() {
        // The unique constraint on name is the safety net if the check above
        // races with another insert.
        rejectDuplicateName(trimmed(name.get()));
    }

    @Override
    protected void doDeactivate(int id) throws SQLException {
        customerRepository.deactivate(id);
    }

    @Override
    protected void doActivate(int id) throws SQLException {
        customerRepository.activate(id);
    }

    @Override
    public boolean itemIsActive(Customer item) {
        return item.isActive();
    }

    @Override
    protected int itemId(Customer item) {
        return item.getId();
    }

    @Override
    protected void populateForm(Customer customer) {
        validateOnEdit = false;
        name.set(customer.getName());
        phone.set(customer.getPhone() == null ? "" : customer.getPhone());
        email.set(customer.getEmail() == null ? "" : customer.getEmail());
        address.set(customer.getAddress() == null ? "" : customer.getAddress());
        setFormTitle(editFormTitle());
        clearErrorMessage();
        clearFieldErrors();
    }

    @Override
    protected void clearForm() {
        validateOnEdit = false;
        name.set("");
        phone.set("");
        email.set("");
        address.set("");
        setFormTitle(newFormTitle());
        clearFieldErrors();
        clearErrorMessage();
    }

    @Override
    protected void refreshFieldErrors() {
        nameError.set(validateName());
        phoneError.set(validatePhone());
        emailError.set(validateEmail());
        addressError.set(validateAddress());
    }

    @Override
    protected void clearFieldErrors() {
        nameError.set("");
        phoneError.set("");
        emailError.set("");
        addressError.set("");
    }

    @Override
    protected boolean hasFieldErrors() {
        return !nameError.get().isBlank() || !phoneError.get().isBlank()
                || !emailError.get().isBlank() || !addressError.get().isBlank();
    }

    @Override
    protected String fieldErrorsSummary() {
        return joinedErrors(nameError.get(), phoneError.get(), emailError.get(), addressError.get());
    }

    @Override
    protected String countText() {
        return "Clientes: " + filteredItems.size();
    }

    @Override
    protected String newFormTitle() {
        return "Nuevo cliente";
    }

    @Override
    protected String editFormTitle() {
        return "Editar cliente";
    }

    @Override
    public String deactivateLabel() {
        return TOGGLE_DEACTIVATE_LABEL;
    }

    @Override
    protected String activateLabel() {
        return TOGGLE_ACTIVATE_LABEL;
    }

    @Override
    protected String notSelectedMessage() {
        return "Selecciona un cliente de la tabla.";
    }

    @Override
    protected String alreadyActiveMessage() {
        return "El cliente ya está activo.";
    }

    @Override
    protected String alreadyInactiveMessage() {
        return "El cliente ya está dado de baja.";
    }

    @Override
    protected String deactivatedMessage(Customer customer) {
        return "Cliente '" + customer.getName() + "' dado de baja.";
    }

    @Override
    protected String activatedMessage(Customer customer) {
        return "Cliente '" + customer.getName() + "' reactivado.";
    }

    @Override
    protected String saveSuccessMessage() {
        return "Cliente guardado correctamente.";
    }

    private String validateName() {
        String value = trimmed(name.get());
        if (value.isEmpty()) {
            return "Introduce el nombre del cliente.";
        }
        if (value.length() > MAX_NAME_LENGTH) {
            return "El nombre no puede superar los " + MAX_NAME_LENGTH + " caracteres.";
        }
        return "";
    }

    private String validatePhone() {
        String value = trimmed(phone.get());
        if (value.length() > MAX_PHONE_LENGTH) {
            return "El teléfono no puede superar los " + MAX_PHONE_LENGTH + " caracteres.";
        }
        if (!value.isEmpty() && !ContactValidation.isValidPhone(value)) {
            return "El teléfono solo puede contener números y los signos +, -, ( ), punto o espacios.";
        }
        return "";
    }

    private String validateEmail() {
        String value = trimmed(email.get());
        if (value.length() > MAX_EMAIL_LENGTH) {
            return "El correo no puede superar los " + MAX_EMAIL_LENGTH + " caracteres.";
        }
        if (!value.isEmpty() && !ContactValidation.isValidEmail(value)) {
            return "Introduce un correo electrónico válido.";
        }
        return "";
    }

    private String validateAddress() {
        String value = trimmed(address.get());
        if (value.length() > MAX_ADDRESS_LENGTH) {
            return "La dirección no puede superar los " + MAX_ADDRESS_LENGTH + " caracteres.";
        }
        return "";
    }

    private void rejectDuplicateName(String nameValue) {
        String message = "El cliente '" + nameValue + "' ya existe.";
        nameError.set(message);
        fail(message);
    }

    private static String blankToNull(String value) {
        return value.isEmpty() ? null : value;
    }
}
