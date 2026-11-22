package com.entidaddesconocida.monte_erp.viewmodel;

import com.entidaddesconocida.monte_erp.app.BackgroundTasks;
import com.entidaddesconocida.monte_erp.app.SessionValidator;
import com.entidaddesconocida.monte_erp.domain.model.Contact;
import com.entidaddesconocida.monte_erp.domain.repository.ContactRepository;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * CRUD ViewModel shared by the contact screens: customers (RF-06 / CU-06) and
 * suppliers (RF-05 / CU-05).
 *
 * <p>Both screens have the same four fields ({@code name}, {@code phone},
 * {@code email}, {@code address}), the same rules, the same duplicate-name
 * check and the same logical deletion, so the whole screen lives here once. A
 * concrete subclass only supplies its repository, its entity factory and the
 * texts that name the business noun.</p>
 *
 * <p>The search travels to the database through
 * {@link ContactRepository#findActive(String)} /
 * {@link ContactRepository#findInactive(String)}, so the rows are filtered in
 * the SQL {@code WHERE} and not in memory. The CRUD skeleton (selection ↔ form,
 * messages, toggle, counter and the save/deactivate/activate templates) comes
 * from {@link FilterableCrudViewModel} and {@link CrudViewModel}.</p>
 *
 * @param <T> contact entity managed by the screen
 */
public abstract class ContactFormViewModel<T extends Contact> extends FilterableCrudViewModel<T> {

    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_PHONE_LENGTH = 20;
    private static final int MAX_EMAIL_LENGTH = 100;
    private static final int MAX_ADDRESS_LENGTH = 255;

    private final ContactRepository<T> repository;
    private final ContactLabels labels;

    private final StringProperty name = new SimpleStringProperty("");
    private final StringProperty phone = new SimpleStringProperty("");
    private final StringProperty email = new SimpleStringProperty("");
    private final StringProperty address = new SimpleStringProperty("");

    private final ReadOnlyStringWrapper nameError = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper phoneError = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper emailError = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper addressError = new ReadOnlyStringWrapper("");

    protected ContactFormViewModel(ContactRepository<T> repository, ContactLabels labels,
                                   CrudLabels<T> crudLabels, SessionValidator sessionValidator) {
        this(repository, labels, crudLabels, sessionValidator, BackgroundTasks.synchronous());
    }

    protected ContactFormViewModel(ContactRepository<T> repository, ContactLabels labels,
                                   CrudLabels<T> crudLabels, SessionValidator sessionValidator,
                                   BackgroundTasks backgroundTasks) {
        super(crudLabels, sessionValidator, backgroundTasks);
        this.repository = repository;
        this.labels = labels;
        bindFilter(searchText);
        form().field(nameError, this::validateName)
                .field(phoneError, this::validatePhone)
                .field(emailError, this::validateEmail)
                .field(addressError, this::validateAddress)
                .watch(name, phone, email, address);
        // The phone is the only field validated while typing (decision D1).
        watchLiveField(phone, this::validatePhone, phoneError);
        updateCount();
    }

    /** Creates a brand new contact (born active) from the form values. */
    protected abstract T createContact(String name, String phone, String email, String address);

    /** Copies the selected contact with the edited form values, preserving id and status. */
    protected abstract T copyContact(T source, String name, String phone, String email, String address);

    /** Master list currently loaded (active or inactive contacts). */
    public final ObservableList<T> contacts() {
        return items;
    }

    /** Currently selected contact, or {@code null} when none is selected. */
    public final ObjectProperty<T> selectedContactProperty() {
        return selectedItem;
    }

    public final StringProperty nameProperty() {
        return name;
    }

    /** Optional phone; a blank value is persisted as {@code NULL}. */
    public final StringProperty phoneProperty() {
        return phone;
    }

    /** Optional email; a blank value is persisted as {@code NULL}. */
    public final StringProperty emailProperty() {
        return email;
    }

    /** Optional address; a blank value is persisted as {@code NULL}. */
    public final StringProperty addressProperty() {
        return address;
    }

    /** Footer text with the number of contacts currently shown. */
    public final ReadOnlyStringProperty contactCountProperty() {
        return itemCountProperty();
    }

    /** Empty when the name is valid; the message to show otherwise. */
    public final ReadOnlyStringProperty nameErrorProperty() {
        return nameError.getReadOnlyProperty();
    }

    /** Empty when the phone is valid; the message to show otherwise. */
    public final ReadOnlyStringProperty phoneErrorProperty() {
        return phoneError.getReadOnlyProperty();
    }

    /** Empty when the email is valid; the message to show otherwise. */
    public final ReadOnlyStringProperty emailErrorProperty() {
        return emailError.getReadOnlyProperty();
    }

    /** Empty when the address is valid; the message to show otherwise. */
    public final ReadOnlyStringProperty addressErrorProperty() {
        return addressError.getReadOnlyProperty();
    }

    /** Clears the form so a brand new contact can be typed in. */
    public final void newContact() {
        newItem();
    }

    @Override
    protected final Callable<List<T>> activeQuery() {
        // The criteria are captured here, on the UI thread.
        String search = searchText.get();
        return () -> repository.findActive(search);
    }

    @Override
    protected final Callable<List<T>> inactiveQuery() {
        String search = searchText.get();
        return () -> repository.findInactive(search);
    }

    @Override
    protected final Callable<Void> write() {
        String nameValue = trimmed(name.get());
        String phoneValue = blankToNull(trimmed(phone.get()));
        String emailValue = blankToNull(trimmed(email.get()));
        String addressValue = blankToNull(trimmed(address.get()));
        T selected = selectedItem.get();
        if (selected == null) {
            T created = createContact(nameValue, phoneValue, emailValue, addressValue);
            return () -> {
                repository.insert(created);
                return null;
            };
        }
        // Persist a detached copy: a failed write must never leave the
        // rendered row with values that are not in the database.
        T updated = copyContact(selected, nameValue, phoneValue, emailValue, addressValue);
        return () -> {
            repository.update(updated);
            return null;
        };
    }

    @Override
    protected final boolean validateBusinessRules() throws SQLException {
        // An inactive contact still reserves its name (UNIQUE is global), so
        // the duplicate check spans both states.
        String nameValue = trimmed(name.get());
        T selected = selectedItem.get();
        T existing = repository.findByName(nameValue);
        if (existing != null && (selected == null || existing.getId() != selected.getId())) {
            rejectDuplicateName(nameValue);
            return false;
        }
        return true;
    }

    @Override
    protected final void handleIntegrityViolation() {
        // The unique constraint on name is the safety net if the check above
        // races with another insert.
        rejectDuplicateName(trimmed(name.get()));
    }

    @Override
    protected final void doDeactivate(int id) throws SQLException {
        repository.deactivate(id);
    }

    @Override
    protected final void doActivate(int id) throws SQLException {
        repository.activate(id);
    }

    @Override
    public final boolean itemIsActive(T item) {
        return item.isActive();
    }

    @Override
    protected final int itemId(T item) {
        return item.getId();
    }

    @Override
    protected final void populateForm(T contact) {
        name.set(contact.getName());
        phone.set(valueOrEmpty(contact.getPhone()));
        email.set(valueOrEmpty(contact.getEmail()));
        address.set(valueOrEmpty(contact.getAddress()));
        setFormTitle(editFormTitle());
        clearErrorMessage();
    }

    @Override
    protected final void clearForm() {
        name.set("");
        phone.set("");
        email.set("");
        address.set("");
        setFormTitle(newFormTitle());
        clearErrorMessage();
    }

    @Override
    protected final String countText() {
        return labels.countLabel() + ": " + items.size();
    }

    @Override
    protected final String newFormTitle() {
        return labels.newFormTitle();
    }

    @Override
    protected final String editFormTitle() {
        return labels.editFormTitle();
    }

    private String validateName() {
        String value = trimmed(name.get());
        if (value.isEmpty()) {
            return labels.nameRequiredMessage();
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
        String message = labels.duplicateNameMessage().apply(nameValue);
        nameError.set(message);
        fail(message);
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String blankToNull(String value) {
        return value.isEmpty() ? null : value;
    }
}
