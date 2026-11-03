package com.entidaddesconocida.monte_erp.infrastructure.config;

import com.entidaddesconocida.monte_erp.domain.model.ConnectionSettings;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/**
 * Persistence of the database configuration.
 *
 * <p>The JDBC URL lives in a properties file next to the application (the
 * working directory), under the {@code db.url} key. The database credentials
 * live in the operating system keyring (through {@link CredentialStore}); they
 * never touch the properties file.</p>
 *
 * <p>Reads are lenient (missing values become {@code null}); writes surface the
 * failures so the setup screen can tell the user what happened.</p>
 */
public final class DatabaseConfig {

    public static final String CONFIG_FILE_NAME = "application.properties";
    public static final String KEY_URL = "db.url";

    private static final String ACCOUNT_USER = "db.user";
    private static final String ACCOUNT_PASSWORD = "db.password";

    private final CredentialStore credentialStore;
    private final Path configFile;

    public DatabaseConfig(CredentialStore credentialStore) {
        this(credentialStore, Path.of(CONFIG_FILE_NAME).toAbsolutePath());
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

    /** Creates the properties file when it does not exist yet. */
    public void ensureFileExists() throws IOException {
        if (!Files.exists(configFile)) {
            Files.createFile(configFile);
        }
    }

    /**
     * Loads the persisted settings; missing or unreadable values are
     * {@code null} (never throws).
     */
    public ConnectionSettings load() {
        return new ConnectionSettings(
                blankToNull(readUrl()),
                blankToNull(credentialStore.get(ACCOUNT_USER)),
                blankToNull(credentialStore.get(ACCOUNT_PASSWORD)));
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
        String previousUser = credentialStore.get(ACCOUNT_USER);
        String previousPassword = credentialStore.get(ACCOUNT_PASSWORD);
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

    private void restoreCredential(String account, String previousValue, Throwable failure) {
        try {
            if (previousValue == null) {
                credentialStore.delete(account);
            } else {
                credentialStore.set(account, previousValue);
            }
        } catch (CredentialStoreException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
