package com.entidaddesconocida.monte_erp.viewmodel;

import com.entidaddesconocida.monte_erp.viewmodel.support.FakeCrudItem;
import com.entidaddesconocida.monte_erp.viewmodel.support.FakeCrudRepository;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the generic CRUD template ({@link CrudViewModel} and
 * {@link FilterableCrudViewModel}).
 *
 * <p>The concrete screens already test the behaviour with their real entities;
 * this suite covers the template itself with a minimal fake entity and an
 * in-memory repository, without a database or the JavaFX toolkit. It pins the
 * parts that are easy to break while refactoring the base: the save template
 * (field validation, business rules and integrity violation), the
 * deactivate/activate guards, the reload failure message, the toggle state and
 * the live validation, plus the filterable layer (search and the
 * "show inactive" rollback).</p>
 */
class CrudViewModelTest {

    private FakeCrudRepository repository;
    private TestViewModel viewModel;

    @BeforeEach
    void setUp() {
        repository = new FakeCrudRepository().withItem(new FakeCrudItem("Uno"));
        viewModel = new TestViewModel(repository);
        viewModel.load();
    }

    @Test
    @DisplayName("save() corta antes de escribir cuando hay errores de campo")
    void saveStopsBeforeWritingOnFieldErrors() {
        viewModel.nameProperty().set("");

        viewModel.save();

        assertEquals("Introduce el nombre del elemento.", viewModel.nameErrorProperty().get());
        assertEquals(1, repository.count());
    }

    @Test
    @DisplayName("save() delega en handleIntegrityViolation() ante una clave única duplicada")
    void saveHandlesIntegrityViolation() {
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
        TestViewModel racingViewModel = new TestViewModel(racing);
        racingViewModel.nameProperty().set("Dos");

        racingViewModel.save();

        assertEquals("El elemento 'Dos' ya existe.", racingViewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("deactivate() sin selección avisa sin tocar el repositorio")
    void deactivateWithoutSelectionFails() {
        viewModel.selectedItemProperty().set(null);

        viewModel.deactivate();

        assertEquals("Selecciona un elemento de la tabla.", viewModel.errorMessageProperty().get());
        assertTrue(viewModel.items.get(0).isActive());
    }

    @Test
    @DisplayName("deactivate() sobre una fila ya inactiva avisa sin escribir")
    void deactivatingAnInactiveItemFails() {
        repository.withItem(new FakeCrudItem("Viejo", false));
        viewModel.showInactiveProperty().set(true);
        viewModel.selectedItemProperty().set(viewModel.items.get(0));

        viewModel.deactivate();

        assertEquals("Ya está dado de baja.", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("activate() sobre una fila ya activa avisa sin escribir")
    void activatingAnActiveItemFails() {
        viewModel.selectedItemProperty().set(viewModel.items.get(0));

        viewModel.activate();

        assertEquals("Ya está activo.", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("Si la recarga falla tras guardar, avisa aparte y resetea el formulario")
    void failedReloadAfterSaveReportsSeparately() {
        viewModel.nameProperty().set("Dos");
        repository.failOn("findActive");

        viewModel.save();

        assertEquals(2, repository.count());
        assertTrue(viewModel.errorMessageProperty().get().contains("se guardaron"));
        assertEquals("", viewModel.nameProperty().get());
        assertNull(viewModel.selectedItemProperty().get());
    }

    @Test
    @DisplayName("deactivate()/activate() recargan la lista y dejan el mensaje de éxito")
    void deactivateAndActivateReloadTheList() {
        viewModel.selectedItemProperty().set(viewModel.items.get(0));

        viewModel.deactivate();
        assertEquals("Elemento 'Uno' dado de baja.", viewModel.statusMessageProperty().get());
        assertNull(viewModel.selectedItemProperty().get());

        viewModel.showInactiveProperty().set(true);
        viewModel.selectedItemProperty().set(viewModel.items.get(0));
        viewModel.activate();
        assertEquals("Elemento 'Uno' reactivado.", viewModel.statusMessageProperty().get());
    }

    @Test
    @DisplayName("El estado del toggle sigue a la selección")
    void toggleStateFollowsSelection() {
        assertTrue(viewModel.toggleDisabledProperty().get());
        assertEquals("Dar de baja", viewModel.toggleLabelProperty().get());

        viewModel.selectedItemProperty().set(viewModel.items.get(0));
        assertFalse(viewModel.toggleDisabledProperty().get());
        assertEquals("Dar de baja", viewModel.toggleLabelProperty().get());

        repository.withItem(new FakeCrudItem("Viejo", false));
        viewModel.showInactiveProperty().set(true);
        assertTrue(viewModel.toggleDisabledProperty().get());

        viewModel.selectedItemProperty().set(viewModel.items.get(0));
        assertEquals("Reactivar", viewModel.toggleLabelProperty().get());
        assertFalse(viewModel.toggleDisabledProperty().get());
    }

    @Test
    @DisplayName("La validación en vivo solo actúa tras el primer intento de guardado")
    void liveValidationStartsAfterFirstSaveAttempt() {
        viewModel.nameProperty().set("");
        assertTrue(viewModel.nameErrorProperty().get().isEmpty());

        viewModel.save();
        assertEquals("Introduce el nombre del elemento.", viewModel.nameErrorProperty().get());

        viewModel.nameProperty().set("Dos");
        assertTrue(viewModel.nameErrorProperty().get().isEmpty());
    }

    @Test
    @DisplayName("showInactive recarga la lista con las filas dadas de baja")
    void showInactiveReloadsTheList() {
        repository.withItem(new FakeCrudItem("Viejo", false));
        viewModel.load();
        assertEquals(1, viewModel.items.size());

        viewModel.showInactiveProperty().set(true);

        assertEquals(1, viewModel.items.size());
        assertEquals("Viejo", viewModel.items.get(0).getName());
    }

    @Test
    @DisplayName("Si la carga inicial falla, el formulario muestra sus valores por defecto")
    void failedLoadKeepsTheFormDefaults() {
        // A fresh ViewModel on purpose: the failure happens before any successful
        // load has set the title/toggle, which is what the base used to leave blank.
        TestViewModel failing = new TestViewModel(new FakeCrudRepository().throwOnAccess());

        failing.load();

        assertTrue(failing.errorMessageProperty().get().contains("No se pudo conectar"));
        assertEquals("Nuevo elemento", failing.formTitleProperty().get());
        assertEquals("Dar de baja", failing.toggleLabelProperty().get());
        assertTrue(failing.toggleDisabledProperty().get());
    }

    @Test
    @DisplayName("Si falla la recarga al mostrar inactivos, la casilla vuelve a su estado")
    void showInactiveRollsBackOnDatabaseError() {
        repository.failOn("findInactive");

        viewModel.showInactiveProperty().set(true);

        assertFalse(viewModel.showInactiveProperty().get());
        assertTrue(viewModel.errorMessageProperty().get().contains("No se pudo conectar"));
    }

    @Test
    @DisplayName("El contador se recalcula una sola vez por cambio en la lista")
    void counterIsRecomputedOncePerListChange() {
        viewModel.countTextCalls = 0;

        viewModel.items.clear();

        assertEquals(1, viewModel.countTextCalls);
        assertEquals("Elementos: 0", viewModel.itemCountProperty().get());
    }

    @Test
    @DisplayName("La búsqueda acota la lista filtrada")
    void searchFiltersTheList() {
        repository.withItem(new FakeCrudItem("Dos"));
        viewModel.load();

        viewModel.searchTextProperty().set("dos");

        assertEquals(1, viewModel.filteredItems.size());
        assertEquals("Dos", viewModel.filteredItems.get(0).getName());
    }

    /**
     * Minimal concrete ViewModel: the entity specific hooks that the real
     * screens implement, with a single validated field.
     */
    private static final class TestViewModel extends FilterableCrudViewModel<FakeCrudItem> {

        private final FakeCrudRepository repository;
        private final StringProperty name = new SimpleStringProperty("");
        private final ReadOnlyStringWrapper nameError = new ReadOnlyStringWrapper("");

        /** Invocations of {@link #countText()}; pins that it runs once per list change. */
        private int countTextCalls;

        TestViewModel(FakeCrudRepository repository) {
            this.repository = repository;
            bindFilter(searchText);
            watchFields(name);
            updateCount();
        }

        StringProperty nameProperty() {
            return name;
        }

        ReadOnlyStringProperty nameErrorProperty() {
            return nameError.getReadOnlyProperty();
        }

        @Override
        protected Callable<List<FakeCrudItem>> activeQuery() {
            String search = searchText.get();
            return () -> repository.findActive(search);
        }

        @Override
        protected Callable<List<FakeCrudItem>> inactiveQuery() {
            String search = searchText.get();
            return () -> repository.findInactive(search);
        }

        @Override
        protected void write() throws SQLException {
            String nameValue = trimmed(name.get());
            FakeCrudItem selected = selectedItem.get();
            if (selected == null) {
                repository.insert(new FakeCrudItem(nameValue));
            } else {
                FakeCrudItem updated = new FakeCrudItem(nameValue, selected.isActive());
                updated.setId(selected.getId());
                repository.update(updated);
            }
        }

        @Override
        protected boolean validateBusinessRules() throws SQLException {
            String nameValue = trimmed(name.get());
            FakeCrudItem selected = selectedItem.get();
            FakeCrudItem existing = repository.findByName(nameValue);
            if (existing != null && (selected == null || existing.getId() != selected.getId())) {
                rejectDuplicate(nameValue);
                return false;
            }
            return true;
        }

        @Override
        protected void handleIntegrityViolation() {
            rejectDuplicate(trimmed(name.get()));
        }

        @Override
        protected void doDeactivate(int id) throws SQLException {
            repository.deactivate(id);
        }

        @Override
        protected void doActivate(int id) throws SQLException {
            repository.activate(id);
        }

        @Override
        public boolean itemIsActive(FakeCrudItem item) {
            return item.isActive();
        }

        @Override
        protected int itemId(FakeCrudItem item) {
            return item.getId();
        }

        @Override
        protected void populateForm(FakeCrudItem item) {
            validateOnEdit = false;
            name.set(item.getName());
            setFormTitle(editFormTitle());
            clearFieldErrors();
            clearErrorMessage();
        }

        @Override
        protected void clearForm() {
            validateOnEdit = false;
            name.set("");
            setFormTitle(newFormTitle());
            clearFieldErrors();
            clearErrorMessage();
        }

        @Override
        protected void refreshFieldErrors() {
            nameError.set(trimmed(name.get()).isEmpty() ? "Introduce el nombre del elemento." : "");
        }

        @Override
        protected void clearFieldErrors() {
            nameError.set("");
        }

        @Override
        protected boolean hasFieldErrors() {
            return !nameError.get().isBlank();
        }

        @Override
        protected String fieldErrorsSummary() {
            return nameError.get();
        }

        @Override
        protected String countText() {
            countTextCalls++;
            return "Elementos: " + filteredItems.size();
        }

        @Override
        protected String newFormTitle() {
            return "Nuevo elemento";
        }

        @Override
        protected String editFormTitle() {
            return "Editar elemento";
        }

        @Override
        public String deactivateLabel() {
            return "Dar de baja";
        }

        @Override
        protected String activateLabel() {
            return "Reactivar";
        }

        @Override
        protected String notSelectedMessage() {
            return "Selecciona un elemento de la tabla.";
        }

        @Override
        protected String alreadyActiveMessage() {
            return "Ya está activo.";
        }

        @Override
        protected String alreadyInactiveMessage() {
            return "Ya está dado de baja.";
        }

        @Override
        protected String deactivatedMessage(FakeCrudItem item) {
            return "Elemento '" + item.getName() + "' dado de baja.";
        }

        @Override
        protected String activatedMessage(FakeCrudItem item) {
            return "Elemento '" + item.getName() + "' reactivado.";
        }

        @Override
        protected String saveSuccessMessage() {
            return "Elemento guardado correctamente.";
        }

        private void rejectDuplicate(String nameValue) {
            String message = "El elemento '" + nameValue + "' ya existe.";
            nameError.set(message);
            fail(message);
        }
    }
}
