package com.entidaddesconocida.monte_erp.infrastructure.config;

import com.entidaddesconocida.monte_erp.infrastructure.ConnectionFactory;

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
 * Lightweight SQL migration runner (no external dependencies).
 *
 * <p>The scripts live in {@code /db/migration} following the
 * {@code V<number>__<description>.sql} convention. On startup the scripts that
 * are not registered yet in the {@code schema_migrations} control table are
 * applied in order, so the application is idempotent: starting it twice does not
 * repeat any work.</p>
 *
 * <p>Scripts are located both in an exploded classpath ({@code target/classes})
 * and inside a JAR, so the same code works with {@code javafx:run} and with the
 * fat JAR produced by the shade plugin.</p>
 *
 * <p><b>Transaction note:</b> every script runs inside a transaction, but MySQL
 * performs an <i>implicit commit</i> on each DDL statement, so the real atomicity
 * of a schema migration depends on the script itself. The row logged in
 * {@code schema_migrations} is transactional with respect to the last statement
 * applied.</p>
 */
public final class MigrationRunner {

    /** Classpath directory holding the published scripts. */
    public static final String MIGRATION_DIR = "db/migration";

    /** Control table listing the migrations already applied. */
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
        // Utility class: never instantiated.
    }

    /**
     * Applies the pending migrations.
     *
     * @param connections factory used to open the connection that runs the scripts
     * @return the number of scripts applied during this run
     * @throws SQLException when a statement fails
     * @throws IOException  when the scripts cannot be read
     */
    public static int migrate(ConnectionFactory connections) throws SQLException, IOException {
        List<String> scripts = discoverScripts();
        if (scripts.isEmpty()) {
            throw new IllegalStateException("No .sql script found in /" + MIGRATION_DIR);
        }

        try (Connection connection = connections.open()) {
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
    }

    /**
     * Versions already registered in {@code schema_migrations}.
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
     * Locates the {@code .sql} scripts of the migration directory, either on an
     * exploded classpath or inside a JAR, and returns them ordered by version
     * number.
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
                default -> throw new IOException("Unsupported protocol for /"
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
                throw new IOException("Resource not found on the classpath: /" + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Version registered for a script: {@code V1__schema.sql -> V1}.
     */
    static String versionOf(String script) {
        String name = script.toLowerCase().endsWith(".sql")
                ? script.substring(0, script.length() - 4)
                : script;
        int separator = name.indexOf("__");
        return separator > 0 ? name.substring(0, separator) : name;
    }

    /** Order by version number and, when equal, alphabetically. */
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
     * Splits a script into statements on {@code ;}, honouring comments
     * ({@code --}, {@code #} and slash-star blocks) and quoted literals.
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
                if (inSingle && next == '\'') { // '' escaped inside a literal
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
