package com.entidaddesconocida.monte_erp.viewmodel;

import com.entidaddesconocida.monte_erp.app.SessionUser;
import com.entidaddesconocida.monte_erp.infrastructure.config.DatabaseUrl;

import de.saxsys.mvvmfx.ViewModel;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;

/**
 * ViewModel of the main screen.
 *
 * <p>Exposes read-only properties derived from the authenticated user and the
 * application configuration. The View binds labels and visibility to these
 * properties. It receives the {@link SessionUser} snapshot, so the screen never
 * has access to the credentials of the account.</p>
 */
public class MainViewModel implements ViewModel {

    private final ReadOnlyStringWrapper welcomeProperty = new ReadOnlyStringWrapper();
    private final ReadOnlyStringWrapper usernameProperty = new ReadOnlyStringWrapper();
    private final ReadOnlyStringWrapper roleLabelProperty = new ReadOnlyStringWrapper();
    private final ReadOnlyBooleanWrapper adminProperty = new ReadOnlyBooleanWrapper();
    private final ReadOnlyStringWrapper databaseProperty = new ReadOnlyStringWrapper();

    public MainViewModel(SessionUser user, DatabaseUrl databaseUrl) {
        welcomeProperty.set("Bienvenido/a, " + user.name());
        usernameProperty.set(user.username());
        roleLabelProperty.set(user.roleLabel());
        adminProperty.set(user.isAdmin());
        databaseProperty.set("Base de datos: " + databaseUrl.redacted());
    }

    public ReadOnlyStringProperty welcomeProperty() {
        return welcomeProperty.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty usernameProperty() {
        return usernameProperty.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty roleLabelProperty() {
        return roleLabelProperty.getReadOnlyProperty();
    }

    public ReadOnlyBooleanProperty adminProperty() {
        return adminProperty.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty databaseProperty() {
        return databaseProperty.getReadOnlyProperty();
    }
}
