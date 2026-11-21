package com.entidaddesconocida.monte_erp.viewmodel;

import com.entidaddesconocida.monte_erp.app.BackgroundTasks;
import com.entidaddesconocida.monte_erp.domain.model.ConnectionSettings;
import com.entidaddesconocida.monte_erp.domain.service.LoadedSetup;
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

    /**
     * Shown when the OS keyring could not be read: the saved password may still
     * exist (or be lost), so the screen must not look like a fresh installation.
     */
    static final String KEYRING_UNAVAILABLE =
            "No se pudo acceder al llavero del sistema; vuelve a introducir la contraseña.";

    /** Local shape check (host and database present); connectivity still needs the server. */
    private static final Pattern JDBC_URL_PATTERN =
            Pattern.compile("^jdbc:mysql://[^/\\s]+/[^\\s?]+(\\?\\S*)?$");

    private final SetupService setupService;
    private final BackgroundTasks backgroundTasks;
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

    /** Guards a save in flight: the migrations must not run twice on a double click. */
    private boolean saveInFlight;

    /**
     * Persistent warning about the keyring; unlike the transient errors it
     * survives {@link #clearMessages()} so the view cannot hide it on init.
     */
    private final String keyringWarning;

    public SetupViewModel(SetupService setupService, boolean onboarding) {
        this(setupService, onboarding, BackgroundTasks.synchronous());
    }

    /**
     * @param backgroundTasks executor of the connection test and of the
     *                        migrations; {@link BackgroundTasks#synchronous()} in
     *                        tests and a virtual-thread executor in the application
     */
    public SetupViewModel(SetupService setupService, boolean onboarding,
                          BackgroundTasks backgroundTasks) {
        this.setupService = setupService;
        this.backgroundTasks = backgroundTasks;
        this.onboarding = onboarding;
        adminSectionVisible.set(onboarding);

        LoadedSetup current = setupService.loadSetup();
        storedSettings = current.settings();
        // An unreadable keyring must not be presented as "nothing configured".
        keyringWarning = current.credentialsUnavailable() ? KEYRING_UNAVAILABLE : "";
        errorMessage.set(keyringWarning);
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
        backgroundTasks.submit(
                () -> {
                    setupService.testConnection(settings);
                    return null;
                },
                ignored -> {
                    statusMessage.set("Conexión correcta.");
                    errorMessage.set("");
                },
                failure -> {
                    statusMessage.set("");
                    errorMessage.set("No se pudo conectar: " + failure.getMessage());
                });
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

        if (saveInFlight) {
            return;
        }
        // The migrations (and the admin user insert) run off the UI thread.
        saveInFlight = true;
        backgroundTasks.submit(
                () -> {
                    if (onboarding) {
                        setupService.provision(settings, adminNameValue, adminUser, adminPass);
                    } else {
                        setupService.save(settings);
                    }
                    return null;
                },
                ignored -> {
                    saveInFlight = false;
                    storedSettings = settings;
                    dbPassword.set("");
                    errorMessage.set("");
                    statusMessage.set("");
                    publish(SETUP_SUCCEEDED);
                },
                failure -> {
                    saveInFlight = false;
                    statusMessage.set("");
                    errorMessage.set("No se pudo guardar la configuración: " + failure.getMessage());
                });
    }

    /**
     * Clears the transient messages (useful on view initialisation). The keyring
     * warning, when present, stays visible: it describes the current state.
     */
    public void clearMessages() {
        errorMessage.set(keyringWarning);
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
        if (!hasValidPort(value.trim())) {
            errorMessage.set(URL_INVALID);
            return false;
        }
        return true;
    }

    /**
     * The URL may omit the port (MySQL defaults to 3306), but when it is typed
     * it must be a whole number in the TCP range: {@code :-3306},
     * {@code :abc} or {@code :70000} are rejected before trying to connect.
     */
    private static boolean hasValidPort(String url) {
        String authority = url.substring("jdbc:mysql://".length());
        int slash = authority.indexOf('/');
        if (slash >= 0) {
            authority = authority.substring(0, slash);
        }
        int at = authority.lastIndexOf('@');
        if (at >= 0) {
            authority = authority.substring(at + 1);
        }
        int portSeparator;
        if (authority.startsWith("[")) {
            // Bracketed IPv6 host: the port, when present, comes after ']'.
            int close = authority.indexOf(']');
            if (close < 0) {
                return false;
            }
            if (close == authority.length() - 1) {
                return true;
            }
            if (authority.charAt(close + 1) != ':') {
                return false;
            }
            portSeparator = close + 1;
        } else {
            portSeparator = authority.lastIndexOf(':');
            if (portSeparator < 0) {
                return true;
            }
        }
        String port = authority.substring(portSeparator + 1);
        if (port.isEmpty() || !port.matches("\\d+")) {
            return false;
        }
        try {
            int value = Integer.parseInt(port);
            return value >= 1 && value <= 65535;
        } catch (NumberFormatException e) {
            return false;
        }
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
