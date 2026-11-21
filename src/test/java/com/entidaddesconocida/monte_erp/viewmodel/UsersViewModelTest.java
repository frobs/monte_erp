package com.entidaddesconocida.monte_erp.viewmodel;

import com.entidaddesconocida.monte_erp.app.BackgroundTasks;
import com.entidaddesconocida.monte_erp.domain.model.Role;
import com.entidaddesconocida.monte_erp.app.SessionState;
import com.entidaddesconocida.monte_erp.app.SessionUser;
import com.entidaddesconocida.monte_erp.app.SessionValidator;
import com.entidaddesconocida.monte_erp.domain.model.User;
import com.entidaddesconocida.monte_erp.security.PasswordHasher;
import com.entidaddesconocida.monte_erp.viewmodel.support.FakeUserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link UsersViewModel} (RF-02).
 *
 * <p>No database, no JavaFX toolkit: the ViewModel is built by hand with a fake
 * repository and the current session user.</p>
 */
class UsersViewModelTest {

    private static final String ADMIN_PASSWORD = "admin123";

    private FakeUserRepository fakeRepository;
    private User admin;
    private UsersViewModel viewModel;

    @BeforeEach
    void setUp() {
        admin = new User("Ana", "ana", PasswordHasher.hash(ADMIN_PASSWORD), Role.ADMIN, true);
        fakeRepository = new FakeUserRepository().withUser(admin);
        viewModel = new UsersViewModel(fakeRepository, SessionUser.of(admin));
        viewModel.load();
    }

    @Test
    @DisplayName("load carga los usuarios y actualiza el contador")
    void loadFillsTheListAndCount() {
        assertEquals(1, viewModel.users().size());
        assertEquals("Usuarios registrados: 1", viewModel.userCountProperty().get());
    }

    @Test
    @DisplayName("Nombre vacío: muestra error de validación")
    void emptyNameShowsError() {
        viewModel.usernameProperty().set("nuevo");
        viewModel.passwordProperty().set("clave123");
        viewModel.save();
        assertEquals("Introduce el nombre del usuario.", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("Usuario vacío: muestra error de validación")
    void emptyUsernameShowsError() {
        viewModel.nameProperty().set("Nuevo Usuario");
        viewModel.passwordProperty().set("clave123");
        viewModel.save();
        assertEquals("Introduce el nombre de usuario.", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("Alta sin contraseña: muestra error de validación")
    void createWithoutPasswordShowsError() {
        viewModel.nameProperty().set("Nuevo Usuario");
        viewModel.usernameProperty().set("nuevo");
        viewModel.save();
        assertEquals("Introduce la contraseña del usuario.", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("Alta válida: inserta el usuario con la contraseña hasheada y rol seleccionado")
    void createValidUser() throws Exception {
        viewModel.nameProperty().set("Nuevo Usuario");
        viewModel.usernameProperty().set("nuevo");
        viewModel.passwordProperty().set("clave123");
        viewModel.roleProperty().set(Role.ADMIN.label());

        viewModel.save();

        assertEquals("", viewModel.errorMessageProperty().get());
        assertTrue(viewModel.statusMessageProperty().get().contains("guardado"));
        User created = fakeRepository.findByUsername("nuevo");
        assertEquals("Nuevo Usuario", created.getName());
        assertTrue(created.isAdmin());
        assertTrue(created.isActive());
        assertTrue(PasswordHasher.verify("clave123", created.getPasswordHash()));
        assertFalse(PasswordHasher.verify("otra", created.getPasswordHash()));
        assertEquals(2, viewModel.users().size());
    }

    @Test
    @DisplayName("Alta con usuario duplicado: error y no se inserta")
    void createDuplicatedUsernameFails() {
        viewModel.nameProperty().set("Otra Ana");
        viewModel.usernameProperty().set("ana");
        viewModel.passwordProperty().set("clave123");

        viewModel.save();

        assertEquals("El nombre de usuario 'ana' ya existe.", viewModel.errorMessageProperty().get());
        assertEquals(1, viewModel.users().size());
    }

    @Test
    @DisplayName("Edición sin contraseña: conserva el hash almacenado")
    void editWithoutPasswordKeepsHash() throws Exception {
        User employee = new User("Bea", "bea", PasswordHasher.hash("bea123"), Role.EMPLOYEE, true);
        fakeRepository.withUser(employee);
        viewModel.load();

        String originalHash = employee.getPasswordHash();
        viewModel.selectedUserProperty().set(employee);
        viewModel.nameProperty().set("Beatriz");
        viewModel.save();

        User updated = fakeRepository.findByUsername("bea");
        assertEquals("Beatriz", updated.getName());
        assertEquals(originalHash, updated.getPasswordHash());
        assertTrue(PasswordHasher.verify("bea123", updated.getPasswordHash()));
    }

    @Test
    @DisplayName("Edición con contraseña: sustituye el hash")
    void editWithPasswordReplacesHash() throws Exception {
        User employee = new User("Bea", "bea", PasswordHasher.hash("bea123"), Role.EMPLOYEE, true);
        fakeRepository.withUser(employee);
        viewModel.load();

        viewModel.selectedUserProperty().set(employee);
        viewModel.passwordProperty().set("nueva456");
        viewModel.save();

        User updated = fakeRepository.findByUsername("bea");
        assertTrue(PasswordHasher.verify("nueva456", updated.getPasswordHash()));
        assertFalse(PasswordHasher.verify("bea123", updated.getPasswordHash()));
    }

    @Test
    @DisplayName("Edición a un usuario ya existente: error de duplicado")
    void editToExistingUsernameFails() throws Exception {
        User employee = new User("Bea", "bea", PasswordHasher.hash("bea123"), Role.EMPLOYEE, true);
        fakeRepository.withUser(employee);
        viewModel.load();

        viewModel.selectedUserProperty().set(employee);
        viewModel.usernameProperty().set("ana");
        viewModel.save();

        assertEquals("El nombre de usuario 'ana' ya existe.", viewModel.errorMessageProperty().get());
        assertEquals("bea", fakeRepository.findByUsername("bea").getUsername());
    }

    @Test
    @DisplayName("Desactivar: el usuario queda inactivo (baja lógica)")
    void deactivateMakesUserInactive() throws Exception {
        User employee = new User("Bea", "bea", PasswordHasher.hash("bea123"), Role.EMPLOYEE, true);
        fakeRepository.withUser(employee);
        viewModel.load();

        viewModel.selectedUserProperty().set(employee);
        viewModel.deactivate();

        assertFalse(fakeRepository.findByUsername("bea").isActive());
        assertTrue(viewModel.statusMessageProperty().get().contains("desactivado"));
        assertEquals("", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("No se puede desactivar el propio usuario administrador")
    void cannotDeactivateCurrentUser() throws Exception {
        viewModel.selectedUserProperty().set(admin);
        viewModel.deactivate();

        assertEquals("No puedes desactivar tu propio usuario.", viewModel.errorMessageProperty().get());
        assertTrue(fakeRepository.findByUsername("ana").isActive());
    }

    @Test
    @DisplayName("Activar: un usuario desactivado vuelve a estar activo")
    void activateMakesUserActiveAgain() throws Exception {
        User employee = new User("Bea", "bea", PasswordHasher.hash("bea123"), Role.EMPLOYEE, false);
        fakeRepository.withUser(employee);
        viewModel.load();

        viewModel.selectedUserProperty().set(employee);
        viewModel.activate();

        assertTrue(fakeRepository.findByUsername("bea").isActive());
        assertTrue(viewModel.statusMessageProperty().get().contains("activado"));
    }

    @Test
    @DisplayName("Error de base de datos al cargar: muestra mensaje de conexión")
    void databaseErrorOnLoadShowsMessage() {
        fakeRepository.throwOnAccess();
        viewModel.load();
        assertTrue(viewModel.errorMessageProperty().get().contains("No se pudo conectar"));
    }

    @Test
    @DisplayName("Error de base de datos al guardar: muestra mensaje de conexión")
    void databaseErrorOnSaveShowsMessage() {
        viewModel.nameProperty().set("Nuevo Usuario");
        viewModel.usernameProperty().set("nuevo");
        viewModel.passwordProperty().set("clave123");
        fakeRepository.throwOnAccess();
        viewModel.save();
        assertTrue(viewModel.errorMessageProperty().get().contains("No se pudo conectar"));
    }

    @Test
    @DisplayName("El estado del botón activar/desactivar depende del usuario seleccionado")
    void toggleStateFollowsSelection() {
        User employee = new User("Bea", "bea", PasswordHasher.hash("bea123"), Role.EMPLOYEE, true);
        fakeRepository.withUser(employee);
        viewModel.load();

        // Nothing selected: disabled, offers deactivation.
        assertTrue(viewModel.toggleDisabledProperty().get());
        assertEquals("Desactivar usuario", viewModel.toggleLabelProperty().get());

        // Another active user: enabled.
        viewModel.selectedUserProperty().set(employee);
        assertFalse(viewModel.toggleDisabledProperty().get());
        assertEquals("Desactivar usuario", viewModel.toggleLabelProperty().get());

        // The current administrator cannot be deactivated.
        viewModel.selectedUserProperty().set(admin);
        assertTrue(viewModel.toggleDisabledProperty().get());

        // A disabled user offers activation, always enabled.
        employee.setActive(false);
        viewModel.selectedUserProperty().set(null);
        viewModel.selectedUserProperty().set(employee);
        assertEquals(UsersViewModel.TOGGLE_ACTIVATE_LABEL, viewModel.toggleLabelProperty().get());
        assertFalse(viewModel.toggleDisabledProperty().get());
    }

    @Test
    @DisplayName("El admin no puede quitarse a sí mismo el rol de administrador")
    void selfDemotionIsRejected() throws Exception {
        viewModel.selectedUserProperty().set(admin);
        viewModel.roleProperty().set(Role.EMPLOYEE.label());

        viewModel.save();

        assertEquals("No puedes quitarte el rol de administrador.", viewModel.errorMessageProperty().get());
        assertTrue(fakeRepository.findByUsername("ana").isAdmin());
        assertEquals(Role.ADMIN, admin.getRole());
    }

    @Test
    @DisplayName("Editar el propio usuario actualiza el snapshot de sesión")
    void editingCurrentUserSyncsSession() throws Exception {
        SessionState session = new SessionState();
        session.signIn(admin);
        UsersViewModel currentSessionViewModel = new UsersViewModel(fakeRepository,
                SessionUser.of(admin), new SessionValidator(session, fakeRepository));
        currentSessionViewModel.load();
        currentSessionViewModel.selectedUserProperty().set(admin);
        currentSessionViewModel.nameProperty().set("Ana María");

        currentSessionViewModel.save();

        assertEquals("Ana María", session.currentUser().name());
        assertEquals("Ana María", fakeRepository.findByUsername("ana").getName());
    }

    @Test
    @DisplayName("A1b: el alta no hashea ni inserta hasta que la tarea de fondo se ejecuta")
    void creatingAUserHashesOffTheCallerThread() throws Exception {
        Deque<Runnable> pending = new ArrayDeque<>();
        UsersViewModel deferred = new UsersViewModel(fakeRepository, SessionUser.of(admin),
                null, deferredTasks(pending));
        deferred.load();
        pending.clear();
        deferred.nameProperty().set("Nuevo Usuario");
        deferred.usernameProperty().set("nuevo");
        deferred.passwordProperty().set("clave123");

        deferred.save();

        // PBKDF2 (210 000 iterations) and the INSERT are inside the task, so the
        // caller returns immediately and the user does not exist yet.
        assertNull(fakeRepository.findByUsername("nuevo"));
        assertEquals("", deferred.statusMessageProperty().get());

        drain(pending);

        User created = fakeRepository.findByUsername("nuevo");
        assertTrue(PasswordHasher.verify("clave123", created.getPasswordHash()));
        assertEquals("Usuario guardado correctamente.", deferred.statusMessageProperty().get());
    }

    @Test
    @DisplayName("A1b: el snapshot de sesión se sincroniza cuando la escritura está confirmada")
    void sessionIsSyncedOnlyAfterTheWriteCommits() throws Exception {
        SessionState session = new SessionState();
        session.signIn(admin);
        Deque<Runnable> pending = new ArrayDeque<>();
        UsersViewModel deferred = new UsersViewModel(fakeRepository, SessionUser.of(admin),
                new SessionValidator(session, fakeRepository), deferredTasks(pending));
        deferred.load();
        pending.clear();
        deferred.selectedUserProperty().set(admin);
        deferred.nameProperty().set("Ana María");

        deferred.save();

        // Still not committed: the top bar must not show a name that is not saved.
        assertEquals("Ana", session.currentUser().name());

        drain(pending);

        assertEquals("Ana María", session.currentUser().name());
        assertEquals("Ana María", fakeRepository.findByUsername("ana").getName());
    }

    /** ViewModel whose tasks wait until the test runs them, like a slow write. */
    private static BackgroundTasks deferredTasks(Deque<Runnable> pending) {
        return new BackgroundTasks() {
            @Override
            public <T> void submit(Callable<T> task, Consumer<T> onSuccess,
                                   Consumer<Throwable> onFailure) {
                pending.add(() -> {
                    try {
                        onSuccess.accept(task.call());
                    } catch (Exception failure) {
                        onFailure.accept(failure);
                    }
                });
            }
        };
    }

    /** Runs every queued task, including the ones they queue themselves. */
    private static void drain(Deque<Runnable> pending) {
        while (!pending.isEmpty()) {
            pending.removeFirst().run();
        }
    }

    @Test
    @DisplayName("Un fallo al guardar la edición no deja la fila con datos sin persistir")
    void failedEditKeepsRowUnchanged() throws Exception {
        User employee = new User("Bea", "bea", PasswordHasher.hash("bea123"), Role.EMPLOYEE, true);
        fakeRepository.withUser(employee);
        viewModel.load();
        viewModel.selectedUserProperty().set(employee);
        viewModel.nameProperty().set("Beatriz");
        fakeRepository.failOn("update");

        viewModel.save();

        assertTrue(viewModel.errorMessageProperty().get().contains("No se pudo conectar"));
        assertEquals("Bea", fakeRepository.findByUsername("bea").getName());
        assertEquals("Bea", viewModel.users().stream()
                .filter(user -> user.getId() == employee.getId())
                .findFirst().orElseThrow().getName());
    }

    @Test
    @DisplayName("Un fallo al activar mantiene al usuario inactivo y el botón ofrece activar")
    void failedActivateKeepsUserInactive() throws Exception {
        User employee = new User("Bea", "bea", PasswordHasher.hash("bea123"), Role.EMPLOYEE, false);
        fakeRepository.withUser(employee);
        viewModel.load();
        viewModel.selectedUserProperty().set(employee);
        fakeRepository.failOn("update");

        viewModel.activate();

        assertTrue(viewModel.errorMessageProperty().get().contains("No se pudo conectar"));
        assertFalse(fakeRepository.findByUsername("bea").isActive());
        assertEquals(UsersViewModel.TOGGLE_ACTIVATE_LABEL, viewModel.toggleLabelProperty().get());
    }

    @Test
    @DisplayName("Un fallo al desactivar mantiene al usuario activo")
    void failedDeactivateKeepsUserActive() throws Exception {
        User employee = new User("Bea", "bea", PasswordHasher.hash("bea123"), Role.EMPLOYEE, true);
        fakeRepository.withUser(employee);
        viewModel.load();
        viewModel.selectedUserProperty().set(employee);
        fakeRepository.failOn("deactivate");

        viewModel.deactivate();

        assertTrue(viewModel.errorMessageProperty().get().contains("No se pudo conectar"));
        assertTrue(fakeRepository.findByUsername("bea").isActive());
    }

    @Test
    @DisplayName("Nombre de más de 100 caracteres: error de validación")
    void nameTooLongShowsError() {
        viewModel.nameProperty().set("x".repeat(101));
        viewModel.usernameProperty().set("nuevo");
        viewModel.passwordProperty().set("clave123");

        viewModel.save();

        assertEquals("El nombre no puede superar los 100 caracteres.",
                viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("Usuario de más de 50 caracteres: error de validación")
    void usernameTooLongShowsError() {
        viewModel.nameProperty().set("Nuevo Usuario");
        viewModel.usernameProperty().set("x".repeat(51));
        viewModel.passwordProperty().set("clave123");

        viewModel.save();

        assertEquals("El nombre de usuario no puede superar los 50 caracteres.",
                viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("Alta con rol Empleado: persiste el rol employee")
    void createWithEmployeeRoleSavesEmployee() throws Exception {
        viewModel.nameProperty().set("Nuevo Empleado");
        viewModel.usernameProperty().set("empleado");
        viewModel.passwordProperty().set("clave123");
        viewModel.roleProperty().set(Role.EMPLOYEE.label());

        viewModel.save();

        User created = fakeRepository.findByUsername("empleado");
        assertEquals(Role.EMPLOYEE, created.getRole());
        assertFalse(created.isAdmin());
    }

    @Test
    @DisplayName("Usuario duplicado con otra capitalización: se rechaza (colación case-insensitive)")
    void caseInsensitiveDuplicateIsRejected() {
        viewModel.nameProperty().set("Otra Ana");
        viewModel.usernameProperty().set("ANA");
        viewModel.passwordProperty().set("clave123");

        viewModel.save();

        assertEquals("El nombre de usuario 'ANA' ya existe.", viewModel.errorMessageProperty().get());
        assertEquals(1, viewModel.users().size());
    }

    @Test
    @DisplayName("Nuevo usuario limpia el formulario y vuelve al rol por defecto")
    void newUserResetsForm() {
        User employee = new User("Bea", "bea", PasswordHasher.hash("bea123"), Role.ADMIN, true);
        fakeRepository.withUser(employee);
        viewModel.load();
        viewModel.selectedUserProperty().set(employee);

        viewModel.newUser();

        assertNull(viewModel.selectedUserProperty().get());
        assertEquals("Nuevo usuario", viewModel.formTitleProperty().get());
        assertEquals("", viewModel.nameProperty().get());
        assertEquals("", viewModel.usernameProperty().get());
        assertEquals("", viewModel.passwordProperty().get());
        assertEquals(Role.EMPLOYEE.label(), viewModel.roleProperty().get());
    }

    @Test
    @DisplayName("Limpiar la selección resetea el formulario a 'Nuevo usuario'")
    void clearingSelectionResetsForm() {
        User employee = new User("Bea", "bea", PasswordHasher.hash("bea123"), Role.EMPLOYEE, true);
        fakeRepository.withUser(employee);
        viewModel.load();
        viewModel.selectedUserProperty().set(employee);
        assertEquals("Editar usuario", viewModel.formTitleProperty().get());

        viewModel.selectedUserProperty().set(null);

        assertEquals("Nuevo usuario", viewModel.formTitleProperty().get());
        assertEquals("", viewModel.nameProperty().get());
    }

    @Test
    @DisplayName("Sin usuarios, el contador muestra cero")
    void emptyTableShowsZeroCount() {
        UsersViewModel emptyViewModel = new UsersViewModel(new FakeUserRepository(), SessionUser.of(admin));

        emptyViewModel.load();

        assertEquals(0, emptyViewModel.users().size());
        assertEquals("Usuarios registrados: 0", emptyViewModel.userCountProperty().get());
    }

    @Test
    @DisplayName("Activar sin selección muestra error")
    void activateWithoutSelectionFails() {
        viewModel.selectedUserProperty().set(null);

        viewModel.activate();

        assertEquals("Selecciona un usuario de la tabla.", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("Desactivar sin selección muestra error")
    void deactivateWithoutSelectionFails() {
        viewModel.selectedUserProperty().set(null);

        viewModel.deactivate();

        assertEquals("Selecciona un usuario de la tabla.", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("Si la recarga falla tras guardar, el alta sigue persistida y se avisa aparte")
    void failedReloadAfterSaveStillReportsTheWrite() throws Exception {
        viewModel.nameProperty().set("Nuevo Usuario");
        viewModel.usernameProperty().set("nuevo");
        viewModel.passwordProperty().set("clave123");
        fakeRepository.failOn("findAll");

        viewModel.save();

        assertEquals("Nuevo Usuario", fakeRepository.findByUsername("nuevo").getName());
        assertTrue(viewModel.errorMessageProperty().get().contains("se guardaron"));
    }

    @Test
    @DisplayName("Desactivar un usuario ya inactivo avisa sin tocar la base de datos")
    void deactivatingAnInactiveUserFails() throws Exception {
        User inactive = new User("Bea", "bea", PasswordHasher.hash("bea123"), Role.EMPLOYEE, false);
        fakeRepository.withUser(inactive);
        viewModel.load();

        viewModel.selectedUserProperty().set(inactive);
        viewModel.deactivate();

        assertEquals("El usuario ya está desactivado.", viewModel.errorMessageProperty().get());
        assertFalse(fakeRepository.findByUsername("bea").isActive());
    }

    @Test
    @DisplayName("Activar un usuario ya activo avisa sin tocar la base de datos")
    void activatingAnActiveUserFails() throws Exception {
        viewModel.selectedUserProperty().set(admin);

        viewModel.activate();

        assertEquals("El usuario ya está activo.", viewModel.errorMessageProperty().get());
        assertTrue(fakeRepository.findByUsername("ana").isActive());
    }

    @Test
    @DisplayName("Las opciones de rol y el texto de contraseña cambian según el formulario")
    void roleOptionsAndPasswordPromptFollowTheForm() {
        assertEquals(2, viewModel.roleOptions().size());
        assertTrue(viewModel.roleOptions().contains(Role.ADMIN.label()));
        assertTrue(viewModel.roleOptions().contains(Role.EMPLOYEE.label()));
        assertEquals("Contraseña", viewModel.passwordPromptProperty().get());

        User employee = new User("Bea", "bea", PasswordHasher.hash("bea123"), Role.EMPLOYEE, true);
        fakeRepository.withUser(employee);
        viewModel.load();
        viewModel.selectedUserProperty().set(employee);

        assertEquals("Dejar en blanco para mantener la actual", viewModel.passwordPromptProperty().get());
    }

    @Test
    @DisplayName("Campos a null se tratan como vacíos (nombre y contraseña)")
    void nullFieldsCountAsEmpty() {
        viewModel.nameProperty().set(null);
        viewModel.usernameProperty().set(null);
        viewModel.passwordProperty().set(null);
        viewModel.save();
        assertEquals("Introduce el nombre del usuario.", viewModel.errorMessageProperty().get());

        viewModel.nameProperty().set("Nuevo Usuario");
        viewModel.usernameProperty().set("nuevo");
        viewModel.save();
        assertEquals("Introduce la contraseña del usuario.", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("Un duplicado detectado por la clave única al insertar se reporta como duplicado")
    void integrityViolationOnInsertIsReportedAsDuplicate() throws Exception {
        FakeUserRepository racing = new FakeUserRepository() {
            @Override
            public User findByUsername(String username) {
                return null;
            }

            @Override
            public int insert(User user) throws SQLException {
                throw new SQLIntegrityConstraintViolationException("Duplicate entry for key 'uq_user_username'");
            }
        };
        UsersViewModel racingViewModel = new UsersViewModel(racing, SessionUser.of(admin));
        racingViewModel.nameProperty().set("Nuevo Usuario");
        racingViewModel.usernameProperty().set("nuevo");
        racingViewModel.passwordProperty().set("clave123");

        racingViewModel.save();

        assertEquals("El nombre de usuario 'nuevo' ya existe.", racingViewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("Una sesión administradora degradada no puede crear usuarios")
    void demotedSessionCannotCreateUsers() throws SQLException {
        User current = new User("Ana", "ana", PasswordHasher.hash(ADMIN_PASSWORD), Role.ADMIN, true);
        FakeUserRepository repository = new FakeUserRepository().withUser(current);
        SessionState session = new SessionState();
        session.signIn(current);
        UsersViewModel guarded = new UsersViewModel(repository, SessionUser.of(current),
                new SessionValidator(session, repository));
        guarded.nameProperty().set("Nuevo Usuario");
        guarded.usernameProperty().set("nuevo");
        guarded.passwordProperty().set("clave123");
        User demoted = new User("Ana", "ana", current.getPasswordHash(), Role.EMPLOYEE, true);
        demoted.setId(current.getId());
        repository.update(demoted);

        guarded.save();

        assertEquals("La gestión de usuarios es exclusiva del administrador.",
                guarded.errorMessageProperty().get());
        assertNull(repository.findByUsername("nuevo"));
    }
}
