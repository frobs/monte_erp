package com.entidaddesconocida.monte_erp.viewmodel;

import com.entidaddesconocida.monte_erp.app.BackgroundTasks;
import com.entidaddesconocida.monte_erp.app.SessionGuard;
import com.entidaddesconocida.monte_erp.viewmodel.support.FakeCrudItem;
import com.entidaddesconocida.monte_erp.viewmodel.support.FakeCrudRepository;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A3b proof: a CRUD screen built <strong>only by composition</strong>, without
 * extending {@link CrudViewModel} or {@link FilterableCrudViewModel}.
 *
 * <p>{@link PlainCrudScreen} is a plain class that holds its own properties, a
 * {@link FormModel} and a {@link CrudActions}, and implements
 * {@link CrudActions.Entity}. The tests pin that the composed screen gets the
 * same behaviour as the inheritance path: load, selection ↔ form, field
 * validation before writing, duplicate rule, save, deactivate/activate and the
 * toggle labels.</p>
 */
class CrudCompositionTest {

    private static final CrudLabels<FakeCrudItem> LABELS = new CrudLabels<>(
            "Dar de baja",
            "Reactivar",
            "Selecciona un elemento de la tabla.",
            "Ya está activo.",
            "Ya está dado de baja.",
            item -> "Elemento '" + item.getName() + "' dado de baja.",
            item -> "Elemento '" + item.getName() + "' reactivado.",
            "Elemento guardado correctamente.");

    private FakeCrudRepository repository;
    private PlainCrudScreen screen;

    @BeforeEach
    void setUp() {
        repository = new FakeCrudRepository().withItem(new FakeCrudItem("Uno"));
        screen = new PlainCrudScreen(repository, BackgroundTasks.synchronous());
        screen.load();
    }

    @Test
    @DisplayName("una pantalla compuesta carga, valida, guarda y recarga sin heredar de CrudViewModel")
    void composedScreenRunsTheWholeSaveTemplate() {
        assertEquals(1, screen.items().size());

        screen.nameProperty().set("");
        screen.save();
        assertEquals("Introduce el nombre del elemento.", screen.errorMessage());
        assertEquals(1, repository.count());

        screen.nameProperty().set("Dos");
        screen.save();

        assertEquals(2, repository.count());
        assertEquals("Elemento guardado correctamente.", screen.statusMessage());
        assertEquals("", screen.nameProperty().get());
        assertEquals(2, screen.items().size());
    }

    @Test
    @DisplayName("una pantalla compuesta rechaza el duplicado de negocio antes de escribir")
    void composedScreenRunsTheBusinessRule() {
        screen.nameProperty().set("uno");
        screen.save();

        assertEquals("El elemento 'uno' ya existe.", screen.errorMessage());
        assertEquals(1, repository.count());
    }

    @Test
    @DisplayName("seleccionar carga el formulario y desactivar/activar usa las etiquetas del record")
    void composedScreenDeactivatesAndActivates() throws SQLException {
        screen.selectedItem().set(screen.items().get(0));
        assertEquals("Uno", screen.nameProperty().get());
        assertEquals("Dar de baja", screen.toggleLabel());

        screen.deactivate();
        assertEquals("Elemento 'Uno' dado de baja.", screen.statusMessage());
        assertFalse(repository.findByName("Uno").isActive());
        assertNull(screen.selectedItem().get());

        screen.showInactive(true);
        screen.refreshItems();
        assertEquals(1, screen.items().size());
        screen.selectedItem().set(screen.items().get(0));
        assertEquals("Reactivar", screen.toggleLabel());

        screen.activate();
        assertEquals("Elemento 'Uno' reactivado.", screen.statusMessage());
        assertTrue(repository.findByName("Uno").isActive());
    }

    @Test
    @DisplayName("la guarda de escritura en vuelo la comparte la pantalla compuesta")
    void composedScreenUsesTheWriteGuard() {
        PendingTasks tasks = new PendingTasks();
        PlainCrudScreen deferred = new PlainCrudScreen(repository, tasks);

        deferred.nameProperty().set("Dos");
        deferred.save();
        deferred.save();

        assertEquals(1, tasks.submitted);
        assertEquals(1, repository.count());
    }

    @Test
    @DisplayName("la pantalla compuesta trata la violación de clave única como un duplicado")
    void composedScreenHandlesIntegrityViolation() {
        FakeCrudRepository racing = new FakeCrudRepository() {
            @Override
            public FakeCrudItem findByName(String name) {
                return null;
            }

            @Override
            public int insert(FakeCrudItem item) throws SQLException {
                throw new SQLIntegrityConstraintViolationException("Duplicate entry");
            }
        };
        PlainCrudScreen racingScreen = new PlainCrudScreen(racing, BackgroundTasks.synchronous());
        racingScreen.nameProperty().set("Dos");

        racingScreen.save();

        assertEquals("El elemento 'Dos' ya existe.", racingScreen.errorMessage());
        assertEquals(0, racing.count());
    }

    @Test
    @DisplayName("el contrato que consume CrudView lo satisface la pantalla compuesta")
    void composedScreenExposesTheViewContract() {
        CrudScreen<FakeCrudItem> contract = screen.viewContract();

        assertEquals(1, contract.items().size());
        contract.selectedItemProperty().set(contract.items().get(0));
        assertEquals("Uno", screen.nameProperty().get());
        assertEquals("Dar de baja", contract.toggleLabelProperty().get());
        assertTrue(contract.itemIsActive(contract.items().get(0)));

        contract.load();
        assertNull(contract.selectedItemProperty().get());
        assertEquals("", screen.nameProperty().get());
    }

    /**
     * Plain screen built by composition: no superclass, only the two
     * collaborators and this entity contract.
     */
    private static final class PlainCrudScreen implements CrudActions.Entity<FakeCrudItem> {

        private static final Logger LOGGER = Logger.getLogger(PlainCrudScreen.class.getName());

        private final FakeCrudRepository repository;
        private final StringProperty name = new SimpleStringProperty("");
        private final ReadOnlyStringWrapper nameError = new ReadOnlyStringWrapper("");
        private final Feedback feedback = new Feedback();
        private final FormModel<FakeCrudItem> form;
        private final CrudActions<FakeCrudItem> actions;
        private boolean showInactive;

        PlainCrudScreen(FakeCrudRepository repository, BackgroundTasks backgroundTasks) {
            this.repository = repository;
            this.form = new FormModel<>(feedback, this::populateForm, this::clearForm);
            this.form.field(nameError, this::validateName).watch(name);
            SessionGuard guard = new SessionGuard(LOGGER, null,
                    ViewMessages.DATABASE_ERROR, ViewMessages.SESSION_EXPIRED);
            this.actions = new CrudActions<>(this, form, LABELS, feedback, guard, backgroundTasks);
            this.actions.watchItemsForCount();
        }

        void load() {
            actions.load();
        }

        void save() {
            actions.save();
        }

        void deactivate() {
            actions.deactivate();
        }

        void activate() {
            actions.activate();
        }

        void refreshItems() {
            actions.refreshItems();
        }

        void showInactive(boolean value) {
            this.showInactive = value;
        }

        CrudScreen<FakeCrudItem> viewContract() {
            return actions;
        }

        StringProperty nameProperty() {
            return name;
        }

        ObservableList<FakeCrudItem> items() {
            return actions.items();
        }

        ObjectProperty<FakeCrudItem> selectedItem() {
            return actions.selectedItem();
        }
        String toggleLabel() {
            return actions.toggleLabelProperty().get();
        }

        String errorMessage() {
            return actions.errorMessageProperty().get();
        }

        String statusMessage() {
            return actions.statusMessageProperty().get();
        }

        @Override
        public Callable<List<FakeCrudItem>> rowsQuery() {
            return showInactive ? repository::findInactive : repository::findActive;
        }

        @Override
        public Callable<Void> write() {
            String value = name.get().trim();
            FakeCrudItem selected = actions.selectedItem().get();
            if (selected == null) {
                FakeCrudItem created = new FakeCrudItem(value);
                return () -> {
                    repository.insert(created);
                    return null;
                };
            }
            FakeCrudItem updated = new FakeCrudItem(value, selected.isActive());
            updated.setId(selected.getId());
            return () -> {
                repository.update(updated);
                return null;
            };
        }

        @Override
        public boolean validateBusinessRules() throws SQLException {
            FakeCrudItem selected = actions.selectedItem().get();
            FakeCrudItem existing = repository.findByName(name.get().trim());
            if (existing != null && (selected == null || existing.getId() != selected.getId())) {
                nameError.set("El elemento '" + name.get().trim() + "' ya existe.");
                feedback.fail(nameError.get());
                return false;
            }
            return true;
        }

        @Override
        public void handleIntegrityViolation() {
            nameError.set("El elemento '" + name.get().trim() + "' ya existe.");
            feedback.fail(nameError.get());
        }

        @Override
        public void doDeactivate(int id) throws SQLException {
            repository.deactivate(id);
        }

        @Override
        public void doActivate(int id) throws SQLException {
            repository.activate(id);
        }

        @Override
        public boolean itemIsActive(FakeCrudItem item) {
            return item.isActive();
        }

        @Override
        public int itemId(FakeCrudItem item) {
            return item.getId();
        }

        @Override
        public String countText() {
            return "Elementos: " + actions.items().size();
        }

        private String validateName() {
            return name.get().trim().isEmpty() ? "Introduce el nombre del elemento." : "";
        }

        private void populateForm(FakeCrudItem item) {
            name.set(item.getName());
            clearError();
        }

        private void clearForm() {
            name.set("");
            clearError();
        }

        private void clearError() {
            // The form messages are owned by Feedback; only the summary is reset here.
            feedback.clearError();
        }
    }

    /** Background tasks double that keeps every task pending. */
    private static final class PendingTasks implements BackgroundTasks {

        private int submitted;

        @Override
        public <T> void submit(Callable<T> task, java.util.function.Consumer<T> onSuccess,
                               java.util.function.Consumer<Throwable> onFailure) {
            submitted++;
        }
    }
}
