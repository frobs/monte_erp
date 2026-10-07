package com.entidaddesconocida.monte_erp.viewmodel;

import com.entidaddesconocida.monte_erp.domain.model.User;
import com.entidaddesconocida.monte_erp.domain.repository.UserRepository;
import com.entidaddesconocida.monte_erp.security.PasswordHasher;

import de.saxsys.mvvmfx.ViewModel;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ViewModel of the user management screen (RF-02 / CU-02).
 *
 * <p>Holds the list of users and the edition form. The administrator can create
 * users, edit them (leaving the password blank keeps the current hash) and
 * disable/reactivate them logically. Users are never deleted physically, as
 * required by the E-R diagram.</p>
 *
 * <p>It never references the View: the confirmation dialog for the destructive
 * action lives in the View, while the business rules (required fields,
 * duplicated username, no self-deactivation or self-demotion) live here.</p>
 */
public class UsersViewModel implements ViewModel {

    private static final Logger LOGGER = Logger.getLogger(UsersViewModel.class.getName());

    /** Label of the destructive toggle action (deactivate the selected user). */
    public static final String TOGGLE_DEACTIVATE_LABEL = "Desactivar usuario";

    /** Label of the recovery toggle action (activate the selected user). */
    public static final String TOGGLE_ACTIVATE_LABEL = "Activar usuario";

    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_USERNAME_LENGTH = 50;
    private static final String DATABASE_ERROR =
            "No se pudo conectar con la base de datos. Comprueba que MySQL esté arrancado.";
    private static final String RELOAD_ERROR =
            "Los cambios se guardaron, pero no se pudo recargar la lista. Pulsa 'Actualizar'.";

    private final UserRepository userRepository;
    private final User currentUser;

    private final ObservableList<User> users = FXCollections.observableArrayList();
    private final ObjectProperty<User> selectedUser = new SimpleObjectProperty<>();

    private final StringProperty name = new SimpleStringProperty("");
    private final StringProperty username = new SimpleStringProperty("");
    private final StringProperty password = new SimpleStringProperty("");
    private final StringProperty role = new SimpleStringProperty(User.ROLE_EMPLOYEE_LABEL);

    private final StringProperty errorMessage = new SimpleStringProperty("");
    private final StringProperty statusMessage = new SimpleStringProperty("");
    private final ReadOnlyStringWrapper formTitle = new ReadOnlyStringWrapper("Nuevo usuario");
    private final ReadOnlyStringWrapper passwordPrompt =
            new ReadOnlyStringWrapper("Contraseña");
    private final ReadOnlyStringWrapper toggleLabel =
            new ReadOnlyStringWrapper(TOGGLE_DEACTIVATE_LABEL);
    private final ReadOnlyBooleanWrapper toggleDisabled = new ReadOnlyBooleanWrapper(true);
    private final ReadOnlyStringWrapper userCount = new ReadOnlyStringWrapper("");
    private final ObservableList<String> roleOptions = FXCollections.unmodifiableObservableList(
            FXCollections.observableArrayList(User.ROLE_ADMIN_LABEL, User.ROLE_EMPLOYEE_LABEL));

    public UsersViewModel(UserRepository userRepository, User currentUser) {
        this.userRepository = userRepository;
        this.currentUser = currentUser;

        // Selecting a row loads it into the form; clearing the selection resets
        // the form so a stale row can never be edited/saved by accident.
        selectedUser.addListener((observable, oldValue, newValue) -> {
            if (newValue == null) {
                clearForm();
                return;
            }
            populateForm(newValue);
            refreshToggleState();
        });

        users.addListener((ListChangeListener<User>) change -> updateCount());
        updateCount();
    }

    /** Users loaded from the database, bound to the table. */
    public ObservableList<User> users() {
        return users;
    }

    /** Currently selected user, or {@code null} when none is selected. */
    public ObjectProperty<User> selectedUserProperty() {
        return selectedUser;
    }

    public StringProperty nameProperty() {
        return name;
    }

    public StringProperty usernameProperty() {
        return username;
    }

    public StringProperty passwordProperty() {
        return password;
    }

    /** Selected role as a user facing label ({@link User#ROLE_ADMIN_LABEL} / {@link User#ROLE_EMPLOYEE_LABEL}). */
    public StringProperty roleProperty() {
        return role;
    }

    /** Options for the role combo box (shared, immutable list). */
    public ObservableList<String> roleOptions() {
        return roleOptions;
    }

    public StringProperty errorMessageProperty() {
        return errorMessage;
    }

    public StringProperty statusMessageProperty() {
        return statusMessage;
    }

    /** {@code "Nuevo usuario"} or {@code "Editar usuario"}, depending on the selection. */
    public ReadOnlyStringProperty formTitleProperty() {
        return formTitle.getReadOnlyProperty();
    }

    /** Password hint: on edition a blank value keeps the stored hash. */
    public ReadOnlyStringProperty passwordPromptProperty() {
        return passwordPrompt.getReadOnlyProperty();
    }

    /** Label of the activate/deactivate button, derived from the selected user. */
    public ReadOnlyStringProperty toggleLabelProperty() {
        return toggleLabel.getReadOnlyProperty();
    }

    /** {@code true} when the activate/deactivate button must be disabled. */
    public ReadOnlyBooleanProperty toggleDisabledProperty() {
        return toggleDisabled.getReadOnlyProperty();
    }

    /** Footer text with the number of registered users. */
    public ReadOnlyStringProperty userCountProperty() {
        return userCount.getReadOnlyProperty();
    }

    /** Loads (or reloads) the user list and resets the form. */
    public void load() {
        errorMessage.set("");
        statusMessage.set("");
        try {
            refreshList();
            resetForm();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Could not load the users", e);
            errorMessage.set(DATABASE_ERROR);
        }
    }

    /** Clears the form so a brand new user can be typed in. */
    public void newUser() {
        errorMessage.set("");
        statusMessage.set("");
        resetForm();
    }

    /**
     * Creates the user typed in the form, or updates the selected one. On
     * edition a blank password keeps the stored hash untouched.
     */
    public void save() {
        String nameValue = trimmed(name.get());
        String usernameValue = trimmed(username.get());
        String passwordValue = password.get() == null ? "" : password.get();
        User selected = selectedUser.get();

        if (nameValue.isEmpty()) {
            fail("Introduce el nombre del usuario.");
            return;
        }
        if (usernameValue.isEmpty()) {
            fail("Introduce el nombre de usuario.");
            return;
        }
        if (nameValue.length() > MAX_NAME_LENGTH) {
            fail("El nombre no puede superar los " + MAX_NAME_LENGTH + " caracteres.");
            return;
        }
        if (usernameValue.length() > MAX_USERNAME_LENGTH) {
            fail("El nombre de usuario no puede superar los " + MAX_USERNAME_LENGTH + " caracteres.");
            return;
        }
        if (selected == null && passwordValue.isEmpty()) {
            fail("Introduce la contraseña del usuario.");
            return;
        }

        String roleValue = toRole(role.get());
        if (selected != null && isCurrentUser(selected) && !User.ROLE_ADMIN.equals(roleValue)) {
            fail("No puedes quitarte el rol de administrador.");
            return;
        }

        try {
            if (usernameAlreadyTaken(usernameValue, selected)) {
                fail(duplicateMessage(usernameValue));
                return;
            }
            if (selected == null) {
                User user = new User(nameValue, usernameValue,
                        PasswordHasher.hash(passwordValue), roleValue, true);
                userRepository.insert(user);
            } else {
                // Persist a detached copy first: a failed write must never leave
                // the rendered row with values that are not in the database.
                User updated = new User(nameValue, usernameValue, selected.getPasswordHash(),
                        roleValue, selected.isActive());
                updated.setId(selected.getId());
                if (!passwordValue.isEmpty()) {
                    updated.setPasswordHash(PasswordHasher.hash(passwordValue));
                }
                userRepository.update(updated);
                syncCurrentUser(updated);
            }
        } catch (SQLIntegrityConstraintViolationException e) {
            // The unique constraint on username is the safety net if the check
            // above races with another insert.
            LOGGER.log(Level.WARNING, "Duplicated username on save", e);
            fail(duplicateMessage(usernameValue));
            return;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Could not save the user", e);
            fail(DATABASE_ERROR);
            return;
        }
        reloadAfterWrite("Usuario guardado correctamente.");
    }

    /** Logical deletion of the selected user (never a physical delete). */
    public void deactivate() {
        User selected = selectedUser.get();
        if (selected == null) {
            fail("Selecciona un usuario de la tabla.");
            return;
        }
        if (!selected.isActive()) {
            fail("El usuario ya está desactivado.");
            return;
        }
        if (isCurrentUser(selected)) {
            fail("No puedes desactivar tu propio usuario.");
            return;
        }
        try {
            userRepository.deactivate(selected.getId());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Could not deactivate the user", e);
            fail(DATABASE_ERROR);
            return;
        }
        reloadAfterWrite("Usuario '" + selected.getUsername() + "' desactivado.");
    }

    /** Brings a previously disabled user back to active. */
    public void activate() {
        User selected = selectedUser.get();
        if (selected == null) {
            fail("Selecciona un usuario de la tabla.");
            return;
        }
        if (selected.isActive()) {
            fail("El usuario ya está activo.");
            return;
        }
        try {
            User updated = new User(selected.getName(), selected.getUsername(),
                    selected.getPasswordHash(), selected.getRole(), true);
            updated.setId(selected.getId());
            userRepository.update(updated);
            syncCurrentUser(updated);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Could not activate the user", e);
            fail(DATABASE_ERROR);
            return;
        }
        reloadAfterWrite("Usuario '" + selected.getUsername() + "' activado.");
    }

    private void refreshList() throws SQLException {
        users.setAll(userRepository.findAll());
    }

    /**
     * Reloads the list after a confirmed write. The write is already committed,
     * so a reload failure must not be reported as a failed save: it gets its own
     * message and the form is still reset.
     */
    private void reloadAfterWrite(String successMessage) {
        try {
            refreshList();
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "The write succeeded but the reload failed", e);
            resetForm();
            fail(RELOAD_ERROR);
            return;
        }
        resetForm();
        succeed(successMessage);
    }

    private boolean usernameAlreadyTaken(String candidate, User edited) throws SQLException {
        User existing = userRepository.findByUsername(candidate);
        return existing != null && (edited == null || existing.getId() != edited.getId());
    }

    private void populateForm(User user) {
        name.set(user.getName());
        username.set(user.getUsername());
        password.set("");
        role.set(user.isAdmin() ? User.ROLE_ADMIN_LABEL : User.ROLE_EMPLOYEE_LABEL);
        formTitle.set("Editar usuario");
        passwordPrompt.set("Dejar en blanco para mantener la actual");
    }

    /** Clears the selection and the form; the listener ends up in {@link #clearForm()}. */
    private void resetForm() {
        selectedUser.set(null);
        clearForm();
    }

    /** Clears the form fields and returns to the "new user" defaults. */
    private void clearForm() {
        name.set("");
        username.set("");
        password.set("");
        role.set(User.ROLE_EMPLOYEE_LABEL);
        formTitle.set("Nuevo usuario");
        passwordPrompt.set("Contraseña");
        errorMessage.set("");
        refreshToggleState();
    }

    /**
     * Keeps the session copy of the user in sync when the administrator edits
     * their own account, so the top bar of the main screen is not stale.
     */
    private void syncCurrentUser(User saved) {
        if (!isCurrentUser(saved)) {
            return;
        }
        currentUser.setName(saved.getName());
        currentUser.setUsername(saved.getUsername());
        currentUser.setPasswordHash(saved.getPasswordHash());
        currentUser.setRole(saved.getRole());
        currentUser.setActive(saved.isActive());
    }

    private boolean isCurrentUser(User user) {
        return currentUser != null && user != null && user.getId() == currentUser.getId();
    }

    private void refreshToggleState() {
        User selected = selectedUser.get();
        if (selected == null) {
            toggleLabel.set(TOGGLE_DEACTIVATE_LABEL);
            toggleDisabled.set(true);
            return;
        }
        if (selected.isActive()) {
            toggleLabel.set(TOGGLE_DEACTIVATE_LABEL);
            toggleDisabled.set(isCurrentUser(selected));
        } else {
            toggleLabel.set(TOGGLE_ACTIVATE_LABEL);
            toggleDisabled.set(false);
        }
    }

    private void updateCount() {
        int total = users.size();
        userCount.set("Usuarios registrados: " + total);
    }

    private void fail(String message) {
        statusMessage.set("");
        errorMessage.set(message);
    }

    private void succeed(String message) {
        errorMessage.set("");
        statusMessage.set(message);
    }

    private static String duplicateMessage(String username) {
        return "El nombre de usuario '" + username + "' ya existe.";
    }

    private static String toRole(String roleLabel) {
        return User.ROLE_ADMIN_LABEL.equals(roleLabel) ? User.ROLE_ADMIN : User.ROLE_EMPLOYEE;
    }

    private static String trimmed(String value) {
        return value == null ? "" : value.trim();
    }
}
