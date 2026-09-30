package com.entidaddesconocida.monte_erp.infrastructure.config;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Aplicador de migraciones SQL ligero (sin dependencias externas).
 *
 * <p>Los scripts viven en {@code /db/migration} siguiendo la convención
 * {@code V<numero>__<descripcion>.sql}. Al arrancar se aplican en orden los que
 * todavía no estén registrados en la tabla de control {@code schema_migrations},
 * de modo que la aplicación es idempotente: arrancar dos veces no repite
 * trabajo.</p>
 *
 * <p>Se localizan los scripts tanto en el classpath explotado
 * ({@code target/classes}) como dentro de un JAR, para que funcione igual con
 * {@code javafx:run} y con el fat JAR generado por el shade plugin.</p>
 *
 * <p><b>Nota transaccional:</b> cada script se ejecuta dentro de una
 * transacción, pero MySQL provoca un <i>commit implícito</i> en cada sentencia
 * DDL, por lo que la atomicidad real de una migración de esquema depende de
 * cada script. El registro en {@code schema_migrations} sí es transaccional
 * respecto a la última sentencia aplicada.</p>
 */
public final class MigrationRunner {

    /** Directorio del classpath donde se publican los scripts. */
    public static final String MIGRATION_DIR = "db/migration";

    /** Tabla de control de migraciones aplicadas. */
    public static final String MIGRATIONS_TABLE = "schema_migrations";

    private static final String CREATE_MIGRATIONS_TABLE = """
            CREATE TABLE IF NOT EXISTS %s (
                version    VARCHAR(64)  NOT NULL,
                script     VARCHAR(255) NOT NULL,
                applied_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (version)
            ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
            """.formatted(MIGRATIONS_TABLE);

    private MigrationRunner() {
        // Utilidad: no se instancia.
    }

    /**
     * Aplica las migraciones pendientes.
     *
     * @return número de scripts aplicados en esta ejecución
     * @throws SQLException si falla alguna sentencia
     * @throws IOException  si no se pueden leer los scripts
     */
    public static int migrate() throws SQLException, IOException {
        List<String> scripts = discoverScripts();
        if (scripts.isEmpty()) {
            throw new IllegalStateException("No se encontró ningún script .sql en /" + MIGRATION_DIR);
        }

        Connection connection = DatabaseConnection.getConnection();
        ensureMigrationsTable(connection);
        Set<String> applied = appliedVersions(connection);

        int appliedNow = 0;
        for (String script : scripts) {
            String version = versionOf(script);
            if (applied.contains(version)) {
                continue;
            }
            applyScript(connection, script, version);
            appliedNow++;
        }
        return appliedNow;
    }

    /**
     * Versiones ya registradas en {@code schema_migrations}.
     */
    static Set<String> appliedVersions(Connection connection) throws SQLException {
        Set<String> versions = new HashSet<>();
        String sql = "SELECT version FROM " + MIGRATIONS_TABLE;
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                versions.add(rs.getString("version"));
            }
        }
        return versions;
    }

    private static void ensureMigrationsTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(CREATE_MIGRATIONS_TABLE);
        }
    }

    private static void applyScript(Connection connection, String script, String version)
            throws SQLException, IOException {
        List<String> statements = splitStatements(readResource(MIGRATION_DIR + "/" + script));
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            try (Statement statement = connection.createStatement()) {
                for (String sql : statements) {
                    statement.execute(sql);
                }
            }
            String insert = "INSERT INTO " + MIGRATIONS_TABLE + " (version, script) VALUES (?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(insert)) {
                ps.setString(1, version);
                ps.setString(2, script);
                ps.executeUpdate();
            }
            connection.commit();
        } catch (SQLException | RuntimeException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    /**
     * Localiza los scripts {@code .sql} del directorio de migraciones, tanto en
     * un classpath explotado como dentro de un JAR, y los devuelve ordenados por
     * número de versión.
     */
    static List<String> discoverScripts() throws IOException {
        URL directory = MigrationRunner.class.getResource("/" + MIGRATION_DIR);
        if (directory == null) {
            directory = MigrationRunner.class.getResource("/" + MIGRATION_DIR + "/");
        }
        List<String> scripts = new ArrayList<>();
        if (directory != null) {
            switch (directory.getProtocol()) {
                case "file" -> collectFromDirectory(directory, scripts);
                case "jar" -> collectFromJar(directory, scripts);
                default -> throw new IOException("Protocolo no soportado para /"
                        + MIGRATION_DIR + ": " + directory);
            }
        }
        scripts.sort(MIGRATION_ORDER);
        return scripts;
    }

    private static void collectFromDirectory(URL directory, List<String> scripts) throws IOException {
        Path path = Paths.get(URI.create(directory.toString()));
        try (var files = Files.list(path)) {
            files.filter(Files::isRegularFile)
                    .map(file -> file.getFileName().toString())
                    .filter(MigrationRunner::isSqlScript)
                    .forEach(scripts::add);
        }
    }

    private static void collectFromJar(URL directory, List<String> scripts) throws IOException {
        String spec = directory.getPath();
        int separator = spec.indexOf("!/");
        String jarSpec = separator >= 0 ? spec.substring(0, separator) : spec;
        Path jarPath = Paths.get(URI.create(jarSpec));
        String prefix = MIGRATION_DIR + "/";
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            jar.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(JarEntry::getName)
                    .filter(name -> name.startsWith(prefix) && !name.substring(prefix.length()).contains("/"))
                    .map(name -> name.substring(prefix.length()))
                    .filter(MigrationRunner::isSqlScript)
                    .forEach(scripts::add);
        }
    }

    private static boolean isSqlScript(String name) {
        return name.toLowerCase().endsWith(".sql");
    }

    private static String readResource(String resource) throws IOException {
        try (InputStream in = MigrationRunner.class.getResourceAsStream("/" + resource)) {
            if (in == null) {
                throw new IOException("Recurso no encontrado en el classpath: /" + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Versión registrada para un script: {@code V1__schema.sql -> V1}.
     */
    static String versionOf(String script) {
        String name = script.toLowerCase().endsWith(".sql")
                ? script.substring(0, script.length() - 4)
                : script;
        int separator = name.indexOf("__");
        return separator > 0 ? name.substring(0, separator) : name;
    }

    /** Orden por número de versión y, a igualdad, alfabético. */
    private static final Comparator<String> MIGRATION_ORDER =
            Comparator.comparingInt(MigrationRunner::versionNumber).thenComparing(Comparator.naturalOrder());

    private static int versionNumber(String script) {
        String version = versionOf(script);
        int start = 0;
        while (start < version.length() && !Character.isDigit(version.charAt(start))) {
            start++;
        }
        int end = start;
        while (end < version.length() && Character.isDigit(version.charAt(end))) {
            end++;
        }
        if (start == end) {
            return Integer.MAX_VALUE;
        }
        try {
            return Integer.parseInt(version.substring(start, end));
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    /**
     * Divide un script en sentencias por {@code ;} respetando comentarios
     * ({@code --}, {@code #} y {@code /* *\/}) y literales entrecomillados.
     */
    static List<String> splitStatements(String script) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingle = false;
        boolean inDouble = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;

        for (int i = 0; i < script.length(); i++) {
            char c = script.charAt(i);
            char next = i + 1 < script.length() ? script.charAt(i + 1) : '\0';

            if (inLineComment) {
                if (c == '\n') {
                    inLineComment = false;
                    current.append(c);
                }
                continue;
            }
            if (inBlockComment) {
                if (c == '*' && next == '/') {
                    inBlockComment = false;
                    i++;
                }
                continue;
            }
            if (!inSingle && !inDouble) {
                if (c == '-' && next == '-') {
                    inLineComment = true;
                    i++;
                    continue;
                }
                if (c == '#') {
                    inLineComment = true;
                    continue;
                }
                if (c == '/' && next == '*') {
                    inBlockComment = true;
                    i++;
                    continue;
                }
                if (c == ';') {
                    addStatement(statements, current);
                    continue;
                }
            }
            if (c == '\'' && !inDouble) {
                if (inSingle && next == '\'') { // '' escapado dentro de un literal
                    current.append(c).append(next);
                    i++;
                    continue;
                }
                inSingle = !inSingle;
            } else if (c == '"' && !inSingle) {
                inDouble = !inDouble;
            }
            current.append(c);
        }
        addStatement(statements, current);
        return statements;
    }

    private static void addStatement(List<String> statements, StringBuilder current) {
        String sql = current.toString().trim();
        if (!sql.isEmpty()) {
            statements.add(sql);
        }
        current.setLength(0);
    }
}
