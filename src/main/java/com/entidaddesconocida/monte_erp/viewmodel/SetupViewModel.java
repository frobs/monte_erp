package com.entidaddesconocida.monte_erp.viewmodel;

import com.entidaddesconocida.monte_erp.domain.model.ConnectionSettings;
import com.entidaddesconocida.monte_erp.domain.service.SetupException;
import com.entidaddesconocida.monte_erp.domain.service.SetupService;
import com.entidaddesconocida.monte_erp.infrastructure.config.DatabaseUrl;

import de.saxsys.mvvmfx.ViewModel;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.util.regex.Pattern;

/**
 * ViewModel of the database configuration screen.
 *
 * <p>It works in two modes. In <em>onboarding</em> mode the administrator user
 * fields are shown and {@link #save()} runs the migrations and creates that
 * user. In <em>edit</em> mode (opened from the login screen) only the database
 * connection is changed.</p>
 */
public class SetupViewModel implements ViewModel {

    /** Notification published when the configuration has been saved and applied. */
    public static final String SETUP_SUCCEEDED = "setupSucceeded";

    private static final String CONNECTION_REQUIRED =
            "Introduce la URL, el usuario y la contraseña.";

    private static final String URL_INVALID =
            "La URL no es válida. Debe tener el formato jdbc:mysql://host:puerto/base_de_datos.";
    private static final String URL_CONTAINS_CREDENTIALS =
            "No incluyas credenciales en la URL JDBC; usa los campos de usuario y contraseña.";

    /** Local shape check (host and database present); connectivity still needs the server. */
    private static final Pattern JDBC_URL_PATTERN =
            Pattern.compile("^jdbc:mysql://[^/\\s]+/[^\\s?]+(\\?\\S*)?$");

    private final SetupService setupService;
    private final boolean onboarding;
    private ConnectionSettings storedSettings;

    private final StringProperty url = new SimpleStringProperty("");
    private final StringProperty dbUser = new SimpleStringProperty("");
    private final StringProperty dbPassword = new SimpleStringProperty("");
    private final StringProperty adminName = new SimpleStringProperty("");
    private final StringProperty adminUsername = new SimpleStringProperty("");
    private final StringProperty adminPassword = new SimpleStringProperty("");
    private final StringProperty errorMessage = new SimpleStringProperty("");
    private final StringProperty statusMessage = new SimpleStringProperty("");
    private final ReadOnlyBooleanWrapper adminSectionVisible = new ReadOnlyBooleanWrapper();

    public SetupViewModel(SetupService setupService, boolean onboarding) {
        this.setupService = setupService;
        this.onboarding = onboarding;
        adminSectionVisible.set(onboarding);

        ConnectionSettings current = setupService.load();
        storedSettings = current == null ? new ConnectionSettings(null, null, null) : current;
        url.set(orEmpty(storedSettings.url()));
        dbUser.set(orEmpty(storedSettings.user()));
        // Never expose the saved secret in a login-accessible configuration form.
        dbPassword.set("");
    }

    public StringProperty urlProperty() {
        return url;
    }

    public StringProperty dbUserProperty() {
        return dbUser;
    }

    public StringProperty dbPasswordProperty() {
        return dbPassword;
    }

    public StringProperty adminNameProperty() {
        return adminName;
    }

    public StringProperty adminUsernameProperty() {
        return adminUsername;
    }

    public StringProperty adminPasswordProperty() {
        return adminPassword;
    }

    public StringProperty errorMessageProperty() {
        return errorMessage;
    }

    public StringProperty statusMessageProperty() {
        return statusMessage;
    }

    public ReadOnlyBooleanProperty adminSectionVisibleProperty() {
        return adminSectionVisible.getReadOnlyProperty();
    }

    /** Tries the current credentials without persisting anything. */
    public void testConnection() {
        ConnectionSettings settings = readForm();
        if (!settings.isComplete()) {
            errorMessage.set(CONNECTION_REQUIRED);
            statusMessage.set("");
            return;
        }
        if (!validateUrl(settings.url())) {
            statusMessage.set("");
            return;
        }
        try {
            setupService.testConnection(settings);
            statusMessage.set("Conexión correcta.");
            errorMessage.set("");
        } catch (SetupException e) {
            statusMessage.set("");
            errorMessage.set("No se pudo conectar: " + e.getMessage());
        }
    }

    /**
     * Saves the form. In onboarding mode it also migrates the database and
     * creates the administrator user; in both cases it publishes
     * {@link #SETUP_SUCCEEDED} on success.
     */
    public void save() {
        ConnectionSettings settings = readForm();
        if (!settings.isComplete()) {
            errorMessage.set(CONNECTION_REQUIRED);
            statusMessage.set("");
            return;
        }
        if (!validateUrl(settings.url())) {
            statusMessage.set("");
            return;
        }
        String adminNameValue = adminName.get() == null ? "" : adminName.get().trim();
        String adminUser = adminUsername.get() == null ? "" : adminUsername.get().trim();
        String adminPass = adminPassword.get() == null ? "" : adminPassword.get();
        if (onboarding && (adminNameValue.isEmpty() || adminUser.isEmpty() || adminPass.isEmpty())) {
            errorMessage.set("Introduce el nombre, el usuario y la contraseña del administrador.");
            statusMessage.set("");
            return;
        }

        try {
            if (onboarding) {
                setupService.provision(settings, adminNameValue, adminUser, adminPass);
            } else {
                setupService.save(settings);
            }
            storedSettings = settings;
            dbPassword.set("");
            errorMessage.set("");
            statusMessage.set("");
            publish(SETUP_SUCCEEDED);
        } catch (SetupException e) {
            statusMessage.set("");
            errorMessage.set("No se pudo guardar la configuración: " + e.getMessage());
        }
    }

    /** Clears the visible messages (useful on view initialisation). */
    public void clearMessages() {
        errorMessage.set("");
        statusMessage.set("");
    }

    private ConnectionSettings readForm() {
        String formUrl = trimToNull(url.get());
        String formUser = trimToNull(dbUser.get());
        String formPassword = dbPassword.get();
        if ((formPassword == null || formPassword.isBlank())
                && same(formUrl, storedSettings.url())
                && same(formUser, storedSettings.user())) {
            // Reuse the stored secret only for the exact endpoint/account already trusted.
            formPassword = storedSettings.password();
        }
        return new ConnectionSettings(formUrl, formUser, formPassword);
    }

    /**
     * Rejects a malformed JDBC URL before calling the service, so an obvious
     * format mistake does not need a (failing) connection attempt against the
     * database to be reported.
     */
    private boolean validateUrl(String value) {
        if (value == null || !JDBC_URL_PATTERN.matcher(value.trim()).matches()) {
            errorMessage.set(URL_INVALID);
            return false;
        }
        if (DatabaseUrl.containsCredentials(value)) {
            errorMessage.set(URL_CONTAINS_CREDENTIALS);
            return false;
        }
        return true;
    }

    private static boolean same(String left, String right) {
        return left != null && right != null && left.equals(right);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }
}
