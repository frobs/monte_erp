package com.entidaddesconocida.monte_erp.viewmodel;

import com.entidaddesconocida.monte_erp.app.BackgroundTasks;
import com.entidaddesconocida.monte_erp.app.SessionValidator;
import com.entidaddesconocida.monte_erp.domain.model.Category;
import com.entidaddesconocida.monte_erp.domain.repository.CategoryRepository;

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
 * ViewModel of the category management screen (RF-04 / CU-04).
 *
 * <p>Holds the category list, the search and the edition form. Categories are
 * disabled logically, never deleted physically (decision D2), because
 * {@code PRODUCT} references {@code CATEGORY} with {@code ON DELETE RESTRICT}.
 * A category given up (inactive) cannot be assigned to a product (decision D4)
 * but is still shown and searchable (decision D5).</p>
 *
 * <p>The search travels to the database through
 * {@link CategoryRepository#findActive(String)} /
 * {@link CategoryRepository#findInactive(String)}, so the rows are filtered in
 * the SQL {@code WHERE} and not in memory.</p>
 *
 * <p>The exception of CU-04 ("a category with associated products cannot be
 * deleted") is superseded by the logical deletion (decision D6): giving a
 * category up is always allowed and the products are not touched.</p>
 *
 * <p>Only the category specific parts live here: the validation rules, the
 * duplicate-name rule and the labels. The CRUD skeleton (selection ↔ form,
 * messages, toggle, counter and the save/deactivate/activate templates) comes
 * from {@link FilterableCrudViewModel} (search + inactive toggle) and
 * {@link CrudViewModel}.</p>
 */
public class CategoriesViewModel extends FilterableCrudViewModel<Category> {

    /** Label of the destructive toggle action (logically delete the selected category). */
    public static final String TOGGLE_DEACTIVATE_LABEL = "Dar de baja categoría";

    /** Label of the recovery toggle action (activate the selected category). */
    public static final String TOGGLE_ACTIVATE_LABEL = "Reactivar categoría";

    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_DESCRIPTION_LENGTH = 255;

    private final CategoryRepository categoryRepository;

    private final StringProperty name = new SimpleStringProperty("");
    private final StringProperty description = new SimpleStringProperty("");

    private final ReadOnlyStringWrapper nameError = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper descriptionError = new ReadOnlyStringWrapper("");

    public CategoriesViewModel(CategoryRepository categoryRepository) {
        this(categoryRepository, null);
    }

    public CategoriesViewModel(CategoryRepository categoryRepository, SessionValidator sessionValidator) {
        this(categoryRepository, sessionValidator, BackgroundTasks.synchronous());
    }

    public CategoriesViewModel(CategoryRepository categoryRepository, SessionValidator sessionValidator,
                        BackgroundTasks backgroundTasks) {
        super(sessionValidator, backgroundTasks);
        this.categoryRepository = categoryRepository;
        bindFilter(searchText);
        watchFields(name, description);
        updateCount();
    }

    /** Master list currently loaded (active or inactive categories). */
    public ObservableList<Category> categories() {
        return items;
    }

    /** Filtered view bound to the table. */
    public FilteredList<Category> filteredCategories() {
        return filteredItems;
    }

    /** Currently selected category, or {@code null} when none is selected. */
    public ObjectProperty<Category> selectedCategoryProperty() {
        return selectedItem;
    }

    public StringProperty nameProperty() {
        return name;
    }

    /** Optional description; a blank value is persisted as {@code NULL}. */
    public StringProperty descriptionProperty() {
        return description;
    }

    /** Footer text with the number of categories currently shown. */
    public ReadOnlyStringProperty categoryCountProperty() {
        return itemCountProperty();
    }

    /** Empty when the name is valid; the message to show otherwise. */
    public ReadOnlyStringProperty nameErrorProperty() {
        return nameError.getReadOnlyProperty();
    }

    /** Empty when the description is valid; the message to show otherwise. */
    public ReadOnlyStringProperty descriptionErrorProperty() {
        return descriptionError.getReadOnlyProperty();
    }

    /** Clears the form so a brand new category can be typed in. */
    public void newCategory() {
        newItem();
    }

    @Override
    protected Callable<List<Category>> activeQuery() {
        // The criteria are captured here, on the UI thread.
        String search = searchText.get();
        return () -> categoryRepository.findActive(search);
    }

    @Override
    protected Callable<List<Category>> inactiveQuery() {
        String search = searchText.get();
        return () -> categoryRepository.findInactive(search);
    }

    @Override
    protected void write() throws SQLException {
        String nameValue = trimmed(name.get());
        String descriptionValue = blankToNull(trimmed(description.get()));
        Category selected = selectedItem.get();
        if (selected == null) {
            // A brand new category is born active (see Category(String, String)).
            categoryRepository.insert(new Category(nameValue, descriptionValue));
        } else {
            // Persist a detached copy: a failed write must never leave the
            // rendered row with values that are not in the database.
            categoryRepository.update(new Category(selected.getId(), nameValue, descriptionValue,
                    selected.isActive()));
        }
    }

    @Override
    protected boolean validateBusinessRules() throws SQLException {
        // An inactive category still reserves its name (UNIQUE is global), so
        // the duplicate check spans both states.
        String nameValue = trimmed(name.get());
        Category selected = selectedItem.get();
        Category existing = categoryRepository.findByName(nameValue);
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
        categoryRepository.deactivate(id);
    }

    @Override
    protected void doActivate(int id) throws SQLException {
        categoryRepository.activate(id);
    }

    @Override
    public boolean itemIsActive(Category item) {
        return item.isActive();
    }

    @Override
    protected int itemId(Category item) {
        return item.getId();
    }

    @Override
    protected void populateForm(Category category) {
        validateOnEdit = false;
        name.set(category.getName());
        description.set(category.getDescription() == null ? "" : category.getDescription());
        setFormTitle(editFormTitle());
        clearErrorMessage();
        clearFieldErrors();
    }

    @Override
    protected void clearForm() {
        validateOnEdit = false;
        name.set("");
        description.set("");
        setFormTitle(newFormTitle());
        clearFieldErrors();
        clearErrorMessage();
    }

    @Override
    protected void refreshFieldErrors() {
        nameError.set(validateName());
        descriptionError.set(validateDescription());
    }

    @Override
    protected void clearFieldErrors() {
        nameError.set("");
        descriptionError.set("");
    }

    @Override
    protected boolean hasFieldErrors() {
        return !nameError.get().isBlank() || !descriptionError.get().isBlank();
    }

    @Override
    protected String fieldErrorsSummary() {
        return joinedErrors(nameError.get(), descriptionError.get());
    }

    @Override
    protected String countText() {
        return "Categorías: " + filteredItems.size();
    }

    @Override
    protected String newFormTitle() {
        return "Nueva categoría";
    }

    @Override
    protected String editFormTitle() {
        return "Editar categoría";
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
        return "Selecciona una categoría de la tabla.";
    }

    @Override
    protected String alreadyActiveMessage() {
        return "La categoría ya está activa.";
    }

    @Override
    protected String alreadyInactiveMessage() {
        return "La categoría ya está dada de baja.";
    }

    @Override
    protected String deactivatedMessage(Category category) {
        return "Categoría '" + category.getName() + "' dada de baja.";
    }

    @Override
    protected String activatedMessage(Category category) {
        return "Categoría '" + category.getName() + "' reactivada.";
    }

    @Override
    protected String saveSuccessMessage() {
        return "Categoría guardada correctamente.";
    }

    private String validateName() {
        String value = trimmed(name.get());
        if (value.isEmpty()) {
            return "Introduce el nombre de la categoría.";
        }
        if (value.length() > MAX_NAME_LENGTH) {
            return "El nombre no puede superar los " + MAX_NAME_LENGTH + " caracteres.";
        }
        return "";
    }

    private String validateDescription() {
        String value = trimmed(description.get());
        if (value.length() > MAX_DESCRIPTION_LENGTH) {
            return "La descripción no puede superar los " + MAX_DESCRIPTION_LENGTH + " caracteres.";
        }
        return "";
    }

    private void rejectDuplicateName(String nameValue) {
        String message = "La categoría '" + nameValue + "' ya existe.";
        nameError.set(message);
        fail(message);
    }

    private static String blankToNull(String value) {
        return value.isEmpty() ? null : value;
    }
}
