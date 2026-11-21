package com.entidaddesconocida.monte_erp.viewmodel;

import com.entidaddesconocida.monte_erp.app.BackgroundTasks;
import com.entidaddesconocida.monte_erp.app.SessionUser;
import com.entidaddesconocida.monte_erp.app.SessionValidator;
import com.entidaddesconocida.monte_erp.domain.model.Role;
import com.entidaddesconocida.monte_erp.domain.model.User;
import com.entidaddesconocida.monte_erp.domain.repository.UserRepository;
import com.entidaddesconocida.monte_erp.security.PasswordHasher;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.Callable;

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
 *
 * <p>Only the user specific parts live here: the field rules, the duplicate
 * username check, the self-protection rules and the session synchronization.
 * The CRUD skeleton comes from {@link CrudViewModel}. This is the only CRUD
 * screen that does not extend {@link FilterableCrudViewModel}: users have no
 * search and no active/inactive visibility toggle.</p>
 */
public class UsersViewModel extends CrudViewModel<User> {

    /** Label of the destructive toggle action (deactivate the selected user). */
    public static final String TOGGLE_DEACTIVATE_LABEL = "Desactivar usuario";

    /** Label of the recovery toggle action (activate the selected user). */
    public static final String TOGGLE_ACTIVATE_LABEL = "Activar usuario";

    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_USERNAME_LENGTH = 50;

    private final UserRepository userRepository;

    /**
     * Snapshot of the signed-in administrator, used when the ViewModel is built
     * without a validator (unit tests). In the application the live snapshot of
     * {@link #sessionValidator()} wins, so a demotion applies immediately.
     */
    private final SessionUser currentUser;

    private final StringProperty name = new SimpleStringProperty("");
    private final StringProperty username = new SimpleStringProperty("");
    private final StringProperty password = new SimpleStringProperty("");
    private final StringProperty roleLabel = new SimpleStringProperty(Role.EMPLOYEE.label());

    private final ReadOnlyStringWrapper passwordPrompt =
            new ReadOnlyStringWrapper("Contraseña");
    private final ObservableList<String> roleOptions = FXCollections.unmodifiableObservableList(
            FXCollections.observableArrayList(Role.ADMIN.label(), Role.EMPLOYEE.label()));

    private final ReadOnlyStringWrapper nameError = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper usernameError = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper passwordError = new ReadOnlyStringWrapper("");

    public UsersViewModel(UserRepository userRepository, SessionUser currentUser) {
        this(userRepository, currentUser, null);
    }

    public UsersViewModel(UserRepository userRepository, SessionUser currentUser,
                          SessionValidator sessionValidator) {
        this(userRepository, currentUser, sessionValidator, BackgroundTasks.synchronous());
    }

    public UsersViewModel(UserRepository userRepository, SessionUser currentUser,
                          SessionValidator sessionValidator, BackgroundTasks backgroundTasks) {
        super(sessionValidator, backgroundTasks);
        this.userRepository = userRepository;
        this.currentUser = currentUser;
        watchItemsForCount();
        watchFields(name, username, password);
        updateCount();
    }

    /** Users loaded from the database, bound to the table. */
    public ObservableList<User> users() {
        return items;
    }

    /** Currently selected user, or {@code null} when none is selected. */
    public ObjectProperty<User> selectedUserProperty() {
        return selectedItem;
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

    /** Selected role as a user facing label ({@link Role#label()}). */
    public StringProperty roleProperty() {
        return roleLabel;
    }

    /** Options for the role combo box (shared, immutable list). */
    public ObservableList<String> roleOptions() {
        return roleOptions;
    }

    /** Password hint: on edition a blank value keeps the stored hash. */
    public ReadOnlyStringProperty passwordPromptProperty() {
        return passwordPrompt.getReadOnlyProperty();
    }

    /** Footer text with the number of registered users. */
    public ReadOnlyStringProperty userCountProperty() {
        return itemCountProperty();
    }

    /** Empty when the name is valid; the message to show otherwise. */
    public ReadOnlyStringProperty nameErrorProperty() {
        return nameError.getReadOnlyProperty();
    }

    /** Empty when the username is valid; the message to show otherwise. */
    public ReadOnlyStringProperty usernameErrorProperty() {
        return usernameError.getReadOnlyProperty();
    }

    /** Empty when the password is valid (blank is valid on edition); the message to show otherwise. */
    public ReadOnlyStringProperty passwordErrorProperty() {
        return passwordError.getReadOnlyProperty();
    }

    /** Clears the form so a brand new user can be typed in. */
    public void newUser() {
        newItem();
    }

    @Override
    protected Callable<List<User>> rowsQuery() {
        return userRepository::findAll;
    }

    @Override
    protected void write() throws SQLException {
        String nameValue = trimmed(name.get());
        String usernameValue = trimmed(username.get());
        String passwordValue = password.get() == null ? "" : password.get();
        User selected = selectedItem.get();
        Role roleValue = toRole(roleLabel.get());
        if (selected == null) {
            User user = new User(nameValue, usernameValue,
                    PasswordHasher.hash(passwordValue), roleValue, true);
            userRepository.insert(user);
        } else {
            // Persist a detached copy first: a failed write must never leave
            // the rendered row with values that are not in the database.
            // A blank password keeps the stored hash untouched.
            User updated = new User(nameValue, usernameValue, selected.getPasswordHash(),
                    roleValue, selected.isActive());
            updated.setId(selected.getId());
            if (!passwordValue.isEmpty()) {
                updated.setPasswordHash(PasswordHasher.hash(passwordValue));
            }
            userRepository.update(updated);
            syncCurrentUser(updated);
        }
    }

    @Override
    protected boolean validateBusinessRules() throws SQLException {
        SessionUser acting = actingUser();
        if (acting == null || !acting.isAdmin()) {
            fail("La gestión de usuarios es exclusiva del administrador.");
            return false;
        }
        User selected = selectedItem.get();
        Role roleValue = toRole(roleLabel.get());
        if (selected != null && isCurrentUser(selected) && roleValue != Role.ADMIN) {
            fail("No puedes quitarte el rol de administrador.");
            return false;
        }
        String usernameValue = trimmed(username.get());
        User existing = userRepository.findByUsername(usernameValue);
        if (existing != null && (selected == null || existing.getId() != selected.getId())) {
            fail(duplicateMessage(usernameValue));
            return false;
        }
        return true;
    }

    @Override
    protected void handleIntegrityViolation() {
        // The unique constraint on username is the safety net if the check
        // above races with another insert.
        fail(duplicateMessage(trimmed(username.get())));
    }

    @Override
    protected void doDeactivate(int id) throws SQLException {
        userRepository.deactivate(id);
    }

    @Override
    protected void doActivate(int id) throws SQLException {
        User selected = selectedItem.get();
        User updated = new User(selected.getName(), selected.getUsername(),
                selected.getPasswordHash(), selected.getRole(), true);
        updated.setId(id);
        userRepository.update(updated);
        syncCurrentUser(updated);
    }

    @Override
    public boolean itemIsActive(User item) {
        return item.isActive();
    }

    @Override
    protected int itemId(User item) {
        return item.getId();
    }

    /** The current administrator cannot deactivate their own account. */
    @Override
    protected boolean canDeactivate(User item) {
        SessionUser acting = actingUser();
        if (acting == null || !acting.isAdmin()) {
            fail("La gestión de usuarios es exclusiva del administrador.");
            return false;
        }
        if (isCurrentUser(item)) {
            fail("No puedes desactivar tu propio usuario.");
            return false;
        }
        return true;
    }

    /** The current administrator cannot be deactivated, so the toggle is disabled for them. */
    @Override
    protected boolean canActivate(User item) {
        SessionUser acting = actingUser();
        if (acting == null || !acting.isAdmin()) {
            fail("La gestión de usuarios es exclusiva del administrador.");
            return false;
        }
        return true;
    }

    @Override
    protected boolean isToggleDisabledForActive(User item) {
        return isCurrentUser(item);
    }

    @Override
    protected void populateForm(User user) {
        validateOnEdit = false;
        name.set(user.getName());
        username.set(user.getUsername());
        password.set("");
        roleLabel.set(user.getRoleLabel());
        setFormTitle(editFormTitle());
        passwordPrompt.set("Dejar en blanco para mantener la actual");
        clearFieldErrors();
    }

    @Override
    protected void clearForm() {
        validateOnEdit = false;
        name.set("");
        username.set("");
        password.set("");
        roleLabel.set(Role.EMPLOYEE.label());
        setFormTitle(newFormTitle());
        passwordPrompt.set("Contraseña");
        clearFieldErrors();
        clearErrorMessage();
    }

    @Override
    protected void refreshFieldErrors() {
        nameError.set(validateName());
        usernameError.set(validateUsername());
        passwordError.set(validatePassword());
    }

    @Override
    protected void clearFieldErrors() {
        nameError.set("");
        usernameError.set("");
        passwordError.set("");
    }

    @Override
    protected boolean hasFieldErrors() {
        return !nameError.get().isBlank() || !usernameError.get().isBlank()
                || !passwordError.get().isBlank();
    }

    /**
     * The field messages are shown both decorated (ValidatorFX in the View) and
     * as a summary. The first one wins so the message text stays exactly as it
     * was before the refactor.
     */
    @Override
    protected String fieldErrorsSummary() {
        if (!nameError.get().isBlank()) {
            return nameError.get();
        }
        return !usernameError.get().isBlank() ? usernameError.get() : passwordError.get();
    }

    @Override
    protected String countText() {
        return "Usuarios registrados: " + items.size();
    }

    @Override
    protected String newFormTitle() {
        return "Nuevo usuario";
    }

    @Override
    protected String editFormTitle() {
        return "Editar usuario";
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
        return "Selecciona un usuario de la tabla.";
    }

    @Override
    protected String alreadyActiveMessage() {
        return "El usuario ya está activo.";
    }

    @Override
    protected String alreadyInactiveMessage() {
        return "El usuario ya está desactivado.";
    }

    @Override
    protected String deactivatedMessage(User user) {
        return "Usuario '" + user.getUsername() + "' desactivado.";
    }

    @Override
    protected String activatedMessage(User user) {
        return "Usuario '" + user.getUsername() + "' activado.";
    }

    @Override
    protected String saveSuccessMessage() {
        return "Usuario guardado correctamente.";
    }

    private String validateName() {
        String value = trimmed(name.get());
        if (value.isEmpty()) {
            return "Introduce el nombre del usuario.";
        }
        if (value.length() > MAX_NAME_LENGTH) {
            return "El nombre no puede superar los " + MAX_NAME_LENGTH + " caracteres.";
        }
        return "";
    }

    private String validateUsername() {
        String value = trimmed(username.get());
        if (value.isEmpty()) {
            return "Introduce el nombre de usuario.";
        }
        if (value.length() > MAX_USERNAME_LENGTH) {
            return "El nombre de usuario no puede superar los " + MAX_USERNAME_LENGTH + " caracteres.";
        }
        return "";
    }

    private String validatePassword() {
        boolean creating = selectedItem.get() == null;
        String value = password.get() == null ? "" : password.get();
        return creating && value.isEmpty() ? "Introduce la contraseña del usuario." : "";
    }

    /**
     * Keeps the session snapshot in step when the administrator edits their own
     * account, so the top bar of the main screen is not stale. Only the session
     * relevant fields travel: the hash of the edited row never enters the
     * session.
     */
    private void syncCurrentUser(User saved) {
        SessionValidator validator = sessionValidator();
        if (validator == null || !isCurrentUser(saved)) {
            return;
        }
        validator.synchronize(saved);
    }

    /**
     * Signed-in user to check the rules against: the live snapshot when the
     * session is validated, or the one the ViewModel was built with in tests.
     */
    private SessionUser actingUser() {
        SessionValidator validator = sessionValidator();
        return validator == null ? currentUser : validator.currentUser();
    }

    private boolean isCurrentUser(User user) {
        SessionUser acting = actingUser();
        return acting != null && user != null && user.getId() == acting.id();
    }

    private static String duplicateMessage(String username) {
        return "El nombre de usuario '" + username + "' ya existe.";
    }

    private static Role toRole(String label) {
        return Role.ADMIN.label().equals(label) ? Role.ADMIN : Role.EMPLOYEE;
    }
}
