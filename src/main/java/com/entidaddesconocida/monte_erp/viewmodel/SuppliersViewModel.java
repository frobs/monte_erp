package com.entidaddesconocida.monte_erp.viewmodel;

import com.entidaddesconocida.monte_erp.domain.model.Supplier;
import com.entidaddesconocida.monte_erp.domain.repository.SupplierRepository;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * ViewModel of the supplier management screen (RF-05 / CU-05).
 *
 * <p>Holds the supplier list, the in-memory search and the edition form.
 * Suppliers are disabled logically, never deleted physically (decision D1),
 * because {@code PURCHASE} references {@code SUPPLIER} with
 * {@code ON DELETE RESTRICT}. A supplier given up (inactive) cannot be assigned
 * to a purchase but is still shown and searchable.</p>
 *
 * <p>Only the supplier specific parts live here: the field rules, the
 * duplicate-name rule and the labels. The CRUD skeleton (selection ↔ form,
 * messages, toggle, counter and the save/deactivate/activate templates) comes
 * from {@link FilterableCrudViewModel} (search + inactive toggle) and
 * {@link CrudViewModel}.</p>
 */
public class SuppliersViewModel extends FilterableCrudViewModel<Supplier> {

    /** Label of the destructive toggle action (logically delete the selected supplier). */
    public static final String TOGGLE_DEACTIVATE_LABEL = "Dar de baja proveedor";

    /** Label of the recovery toggle action (activate the selected supplier). */
    public static final String TOGGLE_ACTIVATE_LABEL = "Reactivar proveedor";

    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_PHONE_LENGTH = 20;
    private static final int MAX_EMAIL_LENGTH = 100;

    /** Pragmatic email shape: text@text.text, no spaces. Not a full RFC validator. */
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final SupplierRepository supplierRepository;

    private final StringProperty name = new SimpleStringProperty("");
    private final StringProperty phone = new SimpleStringProperty("");
    private final StringProperty email = new SimpleStringProperty("");

    private final ReadOnlyStringWrapper nameError = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper phoneError = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper emailError = new ReadOnlyStringWrapper("");

    public SuppliersViewModel(SupplierRepository supplierRepository) {
        this.supplierRepository = supplierRepository;
        bindFilter(searchText);
        watchFields(name, phone, email);
        updateCount();
    }

    /** Master list currently loaded (active or inactive suppliers). */
    public ObservableList<Supplier> suppliers() {
        return items;
    }

    /** Filtered view bound to the table. */
    public FilteredList<Supplier> filteredSuppliers() {
        return filteredItems;
    }

    /** Currently selected supplier, or {@code null} when none is selected. */
    public ObjectProperty<Supplier> selectedSupplierProperty() {
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

    /** Footer text with the number of suppliers currently shown. */
    public ReadOnlyStringProperty supplierCountProperty() {
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

    /** Clears the form so a brand new supplier can be typed in. */
    public void newSupplier() {
        newItem();
    }

    @Override
    protected List<Supplier> loadActive() throws SQLException {
        return supplierRepository.findActive();
    }

    @Override
    protected List<Supplier> loadInactive() throws SQLException {
        return supplierRepository.findInactive();
    }

    @Override
    protected boolean matches(Supplier supplier) {
        String text = trimmed(searchText.get()).toLowerCase(Locale.ROOT);
        if (text.isEmpty()) {
            return true;
        }
        return supplier.getName() != null
                && supplier.getName().toLowerCase(Locale.ROOT).contains(text);
    }

    @Override
    protected void write() throws SQLException {
        String nameValue = trimmed(name.get());
        String phoneValue = blankToNull(trimmed(phone.get()));
        String emailValue = blankToNull(trimmed(email.get()));
        Supplier selected = selectedItem.get();
        if (selected == null) {
            // A brand new supplier is born active (see Supplier(String, String, String)).
            supplierRepository.insert(new Supplier(nameValue, phoneValue, emailValue));
        } else {
            // Persist a detached copy: a failed write must never leave the
            // rendered row with values that are not in the database.
            supplierRepository.update(new Supplier(selected.getId(), nameValue, phoneValue,
                    emailValue, selected.isActive()));
        }
    }

    @Override
    protected boolean validateBusinessRules() throws SQLException {
        // An inactive supplier still reserves its name (UNIQUE is global), so
        // the duplicate check spans both states.
        String nameValue = trimmed(name.get());
        Supplier selected = selectedItem.get();
        Supplier existing = supplierRepository.findByName(nameValue);
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
        supplierRepository.deactivate(id);
    }

    @Override
    protected void doActivate(int id) throws SQLException {
        supplierRepository.activate(id);
    }

    @Override
    public boolean itemIsActive(Supplier item) {
        return item.isActive();
    }

    @Override
    protected int itemId(Supplier item) {
        return item.getId();
    }

    @Override
    protected void populateForm(Supplier supplier) {
        validateOnEdit = false;
        name.set(supplier.getName());
        phone.set(supplier.getPhone() == null ? "" : supplier.getPhone());
        email.set(supplier.getEmail() == null ? "" : supplier.getEmail());
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
        setFormTitle(newFormTitle());
        clearFieldErrors();
        clearErrorMessage();
    }

    @Override
    protected void refreshFieldErrors() {
        nameError.set(validateName());
        phoneError.set(validatePhone());
        emailError.set(validateEmail());
    }

    @Override
    protected void clearFieldErrors() {
        nameError.set("");
        phoneError.set("");
        emailError.set("");
    }

    @Override
    protected boolean hasFieldErrors() {
        return !nameError.get().isBlank() || !phoneError.get().isBlank()
                || !emailError.get().isBlank();
    }

    @Override
    protected String fieldErrorsSummary() {
        return joinedErrors(nameError.get(), phoneError.get(), emailError.get());
    }

    @Override
    protected String countText() {
        return "Proveedores: " + filteredItems.size();
    }

    @Override
    protected String newFormTitle() {
        return "Nuevo proveedor";
    }

    @Override
    protected String editFormTitle() {
        return "Editar proveedor";
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
        return "Selecciona un proveedor de la tabla.";
    }

    @Override
    protected String alreadyActiveMessage() {
        return "El proveedor ya está activo.";
    }

    @Override
    protected String alreadyInactiveMessage() {
        return "El proveedor ya está dado de baja.";
    }

    @Override
    protected String deactivatedMessage(Supplier supplier) {
        return "Proveedor '" + supplier.getName() + "' dado de baja.";
    }

    @Override
    protected String activatedMessage(Supplier supplier) {
        return "Proveedor '" + supplier.getName() + "' reactivado.";
    }

    @Override
    protected String saveSuccessMessage() {
        return "Proveedor guardado correctamente.";
    }

    private String validateName() {
        String value = trimmed(name.get());
        if (value.isEmpty()) {
            return "Introduce el nombre del proveedor.";
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
        return "";
    }

    private String validateEmail() {
        String value = trimmed(email.get());
        if (value.length() > MAX_EMAIL_LENGTH) {
            return "El correo no puede superar los " + MAX_EMAIL_LENGTH + " caracteres.";
        }
        if (!value.isEmpty() && !EMAIL_PATTERN.matcher(value).matches()) {
            return "Introduce un correo electrónico válido.";
        }
        return "";
    }

    private void rejectDuplicateName(String nameValue) {
        String message = "El proveedor '" + nameValue + "' ya existe.";
        nameError.set(message);
        fail(message);
    }

    private static String blankToNull(String value) {
        return value.isEmpty() ? null : value;
    }
}
