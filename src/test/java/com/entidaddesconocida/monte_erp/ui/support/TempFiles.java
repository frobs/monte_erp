package com.entidaddesconocida.monte_erp.ui.support;

import com.entidaddesconocida.monte_erp.infrastructure.config.DatabaseConfig;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Temporary files used by the UI tests. Everything lives under {@code target/},
 * which is already ignored by git.
 */
public final class TempFiles {

    private TempFiles() {
    }

    public static Path directory(String name) {
        Path directory = Path.of("target", "ui-test", name);
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return directory;
    }

    /** Writes an {@code application.properties} pointing at {@code jdbcUrl}. */
    public static Path propertiesFile(String name, String jdbcUrl) {
        Path file = directory("config").resolve(name);
        Properties properties = new Properties();
        properties.setProperty(DatabaseConfig.KEY_URL, jdbcUrl);
        try (OutputStream out = Files.newOutputStream(file)) {
            properties.store(out, "Monte ERP UI test configuration");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return file;
    }
}
