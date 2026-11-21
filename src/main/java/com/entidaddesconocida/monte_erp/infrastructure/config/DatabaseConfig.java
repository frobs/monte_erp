package com.entidaddesconocida.monte_erp.infrastructure.config;

import com.entidaddesconocida.monte_erp.domain.model.ConnectionSettings;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Persistence of the database configuration.
 *
 * <p>The JDBC URL lives in an {@code application.properties} file under the
 * {@code db.url} key. In development it lives in the working directory, as it
 * always did. A packaged application uses the per-user configuration directory
 * instead, because the installation directory is usually read-only:
 * {@code %APPDATA%\Monte ERP} on Windows, {@code ~/Library/Application
 * Support/Monte ERP} on macOS and {@code $XDG_CONFIG_HOME/monte-erp} (or
 * {@code ~/.config/monte-erp}) on Linux.</p>
 *
 * <p>The location can be overridden with the {@code monte.erp.config.dir}
 * system property or the {@code MONTE_ERP_CONFIG_DIR} environment variable. An
 * empty override asks for the platform default above, unless the other source
 * has a non-blank value. The database credentials
 * live in the operating system keyring (through {@link CredentialStore}); they
 * never touch the properties file.</p>
 *
 * <p>Reads are lenient (missing values become {@code null}), but they do not
 * hide a broken keyring: {@link #loadWithStatus()} reports whether the
 * credentials were unreadable, so the setup screen can say so instead of
 * offering a clean onboarding (S4). Writes surface the failures so the setup
 * screen can tell the user what happened.</p>
 */
public final class DatabaseConfig {

    private static final Logger LOGGER = Logger.getLogger(DatabaseConfig.class.getName());
    public static final String CONFIG_FILE_NAME = "application.properties";
    public static final String KEY_URL = "db.url";

    /** Directory override used by packaged launchers and tests. */
    public static final String CONFIG_DIR_PROPERTY = "monte.erp.config.dir";

    /** Environment override for launches where a system property is inconvenient. */
    public static final String CONFIG_DIR_ENV = "MONTE_ERP_CONFIG_DIR";

    private static final String ACCOUNT_USER = "db.user";
    private static final String ACCOUNT_PASSWORD = "db.password";

    private final CredentialStore credentialStore;
    private final Path configFile;

    public DatabaseConfig(CredentialStore credentialStore) {
        this(credentialStore, defaultConfigFile());
    }

    /**
     * Resolves the properties file used when no explicit path is given.
     *
     * <p>Precedence:</p>
     * <ol>
     *   <li>a non-blank {@code monte.erp.config.dir} system property;</li>
     *   <li>a non-blank {@code MONTE_ERP_CONFIG_DIR} environment variable;</li>
     *   <li>the platform per-user configuration directory when either override
     *       is present but blank (this is what the packaged launchers pass);</li>
     *   <li>the working directory, preserving the development behaviour.</li>
     * </ol>
     */
    public static Path defaultConfigFile() {
        String property = System.getProperty(CONFIG_DIR_PROPERTY);
        String environment = System.getenv(CONFIG_DIR_ENV);
        if (property != null && !property.isBlank()) {
            return Path.of(property).toAbsolutePath().resolve(CONFIG_FILE_NAME);
        }
        if (environment != null && !environment.isBlank()) {
            return Path.of(environment).toAbsolutePath().resolve(CONFIG_FILE_NAME);
        }
        if (property != null || environment != null) {
            return defaultUserConfigDirectory().resolve(CONFIG_FILE_NAME);
        }
        return Path.of(CONFIG_FILE_NAME).toAbsolutePath();
    }

    /** Per-user configuration directory of the current platform. */
    static Path defaultUserConfigDirectory() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        Path home = Path.of(System.getProperty("user.home", ".")).toAbsolutePath();
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            Path base = appData == null || appData.isBlank()
                    ? home.resolve("AppData").resolve("Roaming")
                    : Path.of(appData);
            return base.toAbsolutePath().resolve("Monte ERP");
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return home.resolve("Library").resolve("Application Support").resolve("Monte ERP");
        }
        String xdgConfigHome = System.getenv("XDG_CONFIG_HOME");
        Path base = xdgConfigHome == null || xdgConfigHome.isBlank()
                ? home.resolve(".config")
                : Path.of(xdgConfigHome);
        return base.toAbsolutePath().resolve("monte-erp");
    }

    /** Visible for tests: lets them point at a temporary file. */
    public DatabaseConfig(CredentialStore credentialStore, Path configFile) {
        this.credentialStore = credentialStore;
        this.configFile = configFile;
    }

    /** Absolute path of the properties file holding the URL. */
    public Path configFile() {
        return configFile;
    }

    /** Creates the parent directory and the properties file when they do not exist yet. */
    public void ensureFileExists() throws IOException {
        Path parent = configFile.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (!Files.exists(configFile)) {
            Files.createFile(configFile);
        }
    }

    /**
     * Settings read from disk plus what is known about the credential store.
     *
     * @param settings                the values that could be read; missing ones
     *                                are {@code null}
     * @param credentialsUnavailable  {@code true} when the keyring could not be
     *                                read, so a missing user or password does
     *                                <em>not</em> mean "not configured"
     */
    public record LoadedConfiguration(ConnectionSettings settings, boolean credentialsUnavailable) {

        public LoadedConfiguration {
            settings = settings == null
                    ? new ConnectionSettings(null, null, null)
                    : settings;
        }
    }

    /**
     * Loads the persisted settings; missing or unreadable values are
     * {@code null} (never throws).
     *
     * <p>Use {@link #loadWithStatus()} when the caller must tell a missing
     * credential from an unreachable keyring.</p>
     */
    public ConnectionSettings load() {
        return loadWithStatus().settings();
    }

    /**
     * Loads the persisted settings, reporting whether the keyring could be read.
     * Never throws: an unreadable credential file or keyring becomes
     * {@code null} values plus {@code credentialsUnavailable}.
     */
    public LoadedConfiguration loadWithStatus() {
        CredentialLookup user = credentialStore.lookup(ACCOUNT_USER);
        CredentialLookup password = credentialStore.lookup(ACCOUNT_PASSWORD);
        if (user.isUnavailable() || password.isUnavailable()) {
            LOGGER.log(Level.FINE, "Keyring unavailable while loading the database credentials");
        }
        return new LoadedConfiguration(
                new ConnectionSettings(
                        blankToNull(readUrl()),
                        blankToNull(user.value()),
                        blankToNull(password.value())),
                user.isUnavailable() || password.isUnavailable());
    }

    /**
     * Persists the settings: URL to the properties file, credentials to the
     * keyring.
     *
     * @throws IOException                when the properties file cannot be written
     * @throws CredentialStoreException   when a credential cannot be written
     */
    public void save(ConnectionSettings settings) throws IOException {
        if (settings != null && DatabaseUrl.containsCredentials(settings.url())) {
            throw new IllegalArgumentException("No incluyas credenciales en la URL JDBC.");
        }
        CredentialLookup previousUser = credentialStore.lookup(ACCOUNT_USER);
        CredentialLookup previousPassword = credentialStore.lookup(ACCOUNT_PASSWORD);
        try {
            credentialStore.set(ACCOUNT_USER, settings.user());
            credentialStore.set(ACCOUNT_PASSWORD, settings.password());
            // Persist the endpoint last: it must not point at new credentials until
            // both keyring writes have succeeded.
            writeUrl(settings.url());
        } catch (IOException | CredentialStoreException failure) {
            restoreCredential(ACCOUNT_PASSWORD, previousPassword, failure);
            restoreCredential(ACCOUNT_USER, previousUser, failure);
            throw failure;
        }
    }

    private String readUrl() {
        if (!Files.isRegularFile(configFile)) {
            return null;
        }
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(configFile)) {
            properties.load(in);
        } catch (IOException e) {
            return null;
        }
        return properties.getProperty(KEY_URL);
    }

    private void writeUrl(String url) throws IOException {
        Properties properties = new Properties();
        if (Files.isRegularFile(configFile)) {
            try (InputStream in = Files.newInputStream(configFile)) {
                properties.load(in);
            } catch (IOException e) {
                // A broken file is replaced by a fresh one.
            }
        }
        properties.setProperty(KEY_URL, url == null ? "" : url.trim());
        Path absoluteFile = configFile.toAbsolutePath();
        Path parent = absoluteFile.getParent();
        Files.createDirectories(parent);
        Path temporaryFile = Files.createTempFile(parent, CONFIG_FILE_NAME, ".tmp");
        try {
            try (OutputStream out = Files.newOutputStream(temporaryFile)) {
                properties.store(out, "Monte ERP · database connection URL");
            }
            try {
                Files.move(temporaryFile, absoluteFile, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporaryFile, absoluteFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    private void restoreCredential(String account, CredentialLookup previous, Throwable failure) {
        if (previous.isUnavailable()) {
            // The previous value is unknown: restoring would delete a credential
            // that may still be valid, so the keyring is left untouched.
            LOGGER.log(Level.FINE,
                    "Previous value of {0} is unknown (keyring unavailable): skipping rollback", account);
            return;
        }
        try {
            if (previous.isPresent()) {
                credentialStore.set(account, previous.value());
            } else {
                credentialStore.delete(account);
            }
        } catch (CredentialStoreException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
