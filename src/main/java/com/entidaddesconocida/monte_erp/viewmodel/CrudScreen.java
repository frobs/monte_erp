package com.entidaddesconocida.monte_erp.viewmodel;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;

/**
 * Contract the code-behind of a CRUD screen binds against, independent of how
 * the ViewModel is built (A3b).
 *
 * <p>{@code CrudView} used to demand a {@link CrudViewModel}, so a screen built
 * by composition (a plain ViewModel holding a {@link CrudActions} and a
 * {@link FormModel}) still could not reuse the view helper. With this interface
 * the View only needs the observable state and the four actions it wires; the
 * inherited five screens satisfy it through {@link CrudViewModel}, and a
 * composed screen satisfies it directly because {@link CrudActions} implements
 * it.</p>
 *
 * <p>Only what the View actually binds is here. Entity-specific properties
 * (form fields, filter controls, extra tables) stay in the concrete ViewModel.</p>
 *
 * @param <T> entity managed by the screen
 */
public interface CrudScreen<T> {

    /** Master list bound to the table. */
    ObservableList<T> items();

    /** Currently selected item, synchronized with the table selection. */
    ObjectProperty<T> selectedItemProperty();

    StringProperty errorMessageProperty();

    StringProperty statusMessageProperty();

    /** {@code "Nuevo …"} or {@code "Editar …"}, depending on the selection. */
    ReadOnlyStringProperty formTitleProperty();

    /** Label of the activate/deactivate button, derived from the selected item. */
    ReadOnlyStringProperty toggleLabelProperty();

    /** {@code true} when the activate/deactivate button must be disabled. */
    ReadOnlyBooleanProperty toggleDisabledProperty();

    /** Label of the destructive toggle action. */
    String deactivateLabel();

    /** Loads (or reloads) the list and resets the form. */
    void load();

    /** Logical deletion of the selected item. */
    void deactivate();

    /** Brings a previously deleted item back to active. */
    void activate();

    /** {@code true} while the given item is active. */
    boolean itemIsActive(T item);
}
