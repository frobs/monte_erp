package com.entidaddesconocida.monte_erp.ui.support;

import com.entidaddesconocida.monte_erp.infrastructure.config.DatabaseConnection;
import com.entidaddesconocida.monte_erp.infrastructure.config.MigrationRunner;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Throwaway MySQL container used by the UI tests.
 *
 * <p>The container is created with the {@code docker} CLI, so the tests only
 * need a working Docker installation (the same one the README already asks
 * for). It is started once per JVM, the real migrations are applied and
 * {@code db/seed/demo_seed.sql} is loaded with the {@code mysql} client inside
 * the container (the script uses {@code DELIMITER} and a stored procedure, so a
 * plain JDBC splitter cannot run it).</p>
 *
 * <p>After the seed a {@code mysqldump} baseline is stored inside the container
 * so every test can run against exactly the same dataset:
 * {@link #resetToSeed()} drops and recreates the schema and restores the dump.
 * The container is removed by the shutdown hook and by the script that runs the
 * suite, so a finished test run leaves nothing behind.</p>
 */
public final class TestDatabase {

    private static final Logger LOGGER = Logger.getLogger(TestDatabase.class.getName());

    public static final String DATABASE = "monte_erp";
    private static final String USERNAME = "erp_user";
    private static final String PASSWORD = "erp_pass";
    private static final String ROOT_PASSWORD = "root1234";
    private static final String IMAGE_PROPERTY = "mysql.image";
    private static final String DEFAULT_IMAGE = "mysql:8.0";
    private static final String LABEL = "monte-erp-ui-test=true";
    private static final String CONTAINER_PREFIX = "monte-erp-ui-";

    private static final String SEED_RESOURCE = "db/seed/demo_seed.sql";
    private static final String BASELINE_FILE = "/tmp/monte_erp_baseline.sql";

    private static final long STARTUP_TIMEOUT_MILLIS = 180_000;

    private static String container;
    private static String jdbcUrl;
    private static boolean provisioned;

    private TestDatabase() {
    }

    /** Starts the container and leaves a seeded schema ready to be used. */
    public static synchronized void provision() {
        if (provisioned) {
            return;
        }
        String image = System.getProperty(IMAGE_PROPERTY, DEFAULT_IMAGE);
        String name = CONTAINER_PREFIX + ProcessHandle.current().pid() + "-"
                + UUID.randomUUID().toString().substring(0, 8);
        LOGGER.info(() -> "Starting UI-test MySQL container " + name + " (" + image + ")");

        runDocker("run", "-d", "--rm", "--name", name, "--label", LABEL,
                "-e", "MYSQL_ROOT_PASSWORD=" + ROOT_PASSWORD,
                "-e", "MYSQL_DATABASE=" + DATABASE,
                "-e", "MYSQL_USER=" + USERNAME,
                "-e", "MYSQL_PASSWORD=" + PASSWORD,
                "-e", "TZ=Europe/Madrid",
                "-p", "127.0.0.1::3306",
                image);
        container = name;
        Runtime.getRuntime().addShutdownHook(new Thread(TestDatabase::stopQuietly));

        jdbcUrl = "jdbc:mysql://127.0.0.1:" + mappedPort() + "/" + DATABASE
                + "?useUnicode=true&connectionTimeZone=Europe/Madrid";
        waitForJdbc();
        configureConnection();
        try {
            MigrationRunner.migrate();
        } catch (SQLException | IOException e) {
            throw new IllegalStateException("Could not apply the migrations on the UI-test database", e);
        }

        seed();
        dumpBaseline();
        verifySeed();
        provisioned = true;
        LOGGER.info(() -> "UI-test database ready at " + jdbcUrl);
    }

    /** Drops the schema and restores the seeded baseline (deterministic per test). */
    public static synchronized void resetToSeed() {
        provision();
        DatabaseConnection.close();
        execInContainer("sh", "-c", "mysql -uroot -p" + ROOT_PASSWORD + " -e \"DROP DATABASE IF EXISTS "
                + DATABASE + "; CREATE DATABASE " + DATABASE
                + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci\""
                + " && mysql -uroot -p" + ROOT_PASSWORD + " " + DATABASE + " < " + BASELINE_FILE);
        configureConnection();
    }

    /**
     * Creates an empty schema (owned by the application user) on the same
     * container, for tests that need a database without the demo seed.
     */
    public static synchronized void createSchema(String schema) {
        provision();
        execInContainer("sh", "-c", "mysql -uroot -p" + ROOT_PASSWORD + " -e \"DROP DATABASE IF EXISTS "
                + schema + "; CREATE DATABASE " + schema
                + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; "
                + "GRANT ALL PRIVILEGES ON " + schema + ".* TO '" + USERNAME + "'@'%'\"");
    }

    /** JDBC URL of another schema of the same container. */
    public static String jdbcUrl(String schema) {
        return jdbcUrl().replace("/" + DATABASE, "/" + schema);
    }

    /** Points the application's shared connection at the container. */
    public static void configureConnection() {
        DatabaseConnection.configure(jdbcUrl(), USERNAME, PASSWORD);
    }

    public static String jdbcUrl() {
        requireProvisioned();
        return jdbcUrl;
    }

    public static String username() {
        return USERNAME;
    }

    public static String password() {
        return PASSWORD;
    }

    // ---------------------------------------------------------------- queries

    public static long longValue(String sql) {
        try (Statement statement = DatabaseConnection.getConnection().createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : -1;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not run: " + sql, e);
        }
    }

    public static String stringValue(String sql) {
        try (Statement statement = DatabaseConnection.getConnection().createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not run: " + sql, e);
        }
    }

    public static List<Map<String, Object>> rows(String sql) {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Statement statement = DatabaseConnection.getConnection().createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            ResultSetMetaData meta = rs.getMetaData();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    row.put(meta.getColumnLabel(i), rs.getObject(i));
                }
                rows.add(row);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not run: " + sql, e);
        }
        return rows;
    }

    public static void execute(String sql) {
        try (Statement statement = DatabaseConnection.getConnection().createStatement()) {
            statement.execute(sql);
        } catch (SQLException e) {
            throw new IllegalStateException("Could not run: " + sql, e);
        }
    }

    // --------------------------------------------------------------- internal

    /**
     * Waits until the final MySQL server accepts authenticated TCP connections.
     *
     * <p>The image first runs a temporary <em>socket-only</em> server to create
     * the database and the user; a socket-level {@code mysqladmin ping} succeeds
     * during that phase, so the readiness check has to be a JDBC login.</p>
     */
    private static void waitForJdbc() {
        long deadline = System.currentTimeMillis() + STARTUP_TIMEOUT_MILLIS;
        SQLException last = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                DatabaseConnection.testConnection(jdbcUrl, USERNAME, PASSWORD);
                return;
            } catch (SQLException notReady) {
                last = notReady;
                if (!isContainerRunning()) {
                    throw new IllegalStateException("The MySQL container stopped unexpectedly", last);
                }
                sleep(1000);
            }
        }
        throw new IllegalStateException("MySQL did not accept connections in time", last);
    }

    private static boolean isContainerRunning() {
        try {
            return "true".equals(runDocker("inspect", "--format", "{{.State.Running}}", container));
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static String mappedPort() {
        String mapping = runDocker("port", container, "3306/tcp");
        int colon = mapping.lastIndexOf(':');
        if (colon < 0) {
            throw new IllegalStateException("Could not read the mapped port: " + mapping);
        }
        return mapping.substring(colon + 1).trim();
    }

    private static void seed() {
        Path log = TempFiles.directory("logs").resolve("seed.log");
        List<String> command = List.of("docker", "exec", "-i", container,
                "mysql", "-uroot", "-p" + ROOT_PASSWORD,
                "--default-character-set=utf8mb4", DATABASE);
        try (InputStream seed = TestDatabase.class.getClassLoader()
                .getResourceAsStream(SEED_RESOURCE)) {
            if (seed == null) {
                throw new IllegalStateException("Classpath resource not found: " + SEED_RESOURCE);
            }
            ProcessBuilder builder = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(log.toFile());
            Process process = builder.start();
            try (OutputStream stdin = process.getOutputStream()) {
                seed.transferTo(stdin);
            }
            int exit = process.waitFor();
            if (exit != 0) {
                throw new IllegalStateException("The seed failed (exit " + exit + "):"
                        + System.lineSeparator()
                        + Files.readString(log, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not load the seed script", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while seeding the database", e);
        }
    }

    private static void dumpBaseline() {
        execInContainer("sh", "-c", "mysqldump -uroot -p" + ROOT_PASSWORD
                + " --single-transaction --routines --triggers " + DATABASE + " > " + BASELINE_FILE);
    }

    private static void verifySeed() {
        expect("USER", SeedFacts.USERS);
        expect("CATEGORY", SeedFacts.CATEGORIES);
        expect("SUPPLIER", SeedFacts.SUPPLIERS);
        expect("CUSTOMER", SeedFacts.CUSTOMERS);
        expect("PRODUCT", SeedFacts.PRODUCTS);
        expect("PURCHASE", SeedFacts.PURCHASES);
        expect("STOCK", SeedFacts.STOCK_LOTS);
        expect("SALES_ORDER", SeedFacts.SALES_ORDERS);
        expect("SALES_ORDER_LINE", SeedFacts.SALES_ORDER_LINES);
        expect("STOCK_ISSUE", SeedFacts.STOCK_ISSUES);

        long alerts = longValue("""
                SELECT COUNT(*) FROM (
                    SELECT p.id
                      FROM PRODUCT p
                      LEFT JOIN STOCK s ON s.product_id = p.id
                     WHERE p.active = 1
                     GROUP BY p.id, p.min_stock
                    HAVING COALESCE(SUM(s.available_quantity), 0) < p.min_stock
                ) alerts
                """);
        if (alerts != SeedFacts.STOCK_ALERTS) {
            throw new IllegalStateException("Seed drift: expected " + SeedFacts.STOCK_ALERTS
                    + " stock alerts but found " + alerts);
        }
    }

    private static void expect(String table, int expected) {
        long actual = longValue("SELECT COUNT(*) FROM " + table);
        if (actual != expected) {
            throw new IllegalStateException("Seed drift: expected " + expected + " rows in "
                    + table + " but found " + actual);
        }
    }

    private static void requireProvisioned() {
        if (container == null) {
            throw new IllegalStateException("TestDatabase.provision() has not been called yet");
        }
    }

    private static String execInContainer(String... command) {
        List<String> full = new ArrayList<>();
        full.add("docker");
        full.add("exec");
        full.add(container);
        full.addAll(List.of(command));
        return runCommand(full);
    }

    private static String runDocker(String... arguments) {
        List<String> command = new ArrayList<>();
        command.add("docker");
        command.addAll(List.of(arguments));
        return runCommand(command);
    }

    private static String runCommand(List<String> command) {
        Path log = TempFiles.directory("logs").resolve("docker.log");
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exit = process.waitFor();
            Files.writeString(log, String.join(" ", command) + System.lineSeparator() + output,
                    StandardCharsets.UTF_8);
            if (exit != 0) {
                throw new IllegalStateException("Command failed (" + exit + "): "
                        + String.join(" ", command) + System.lineSeparator() + output);
            }
            return output.trim();
        } catch (IOException e) {
            throw new IllegalStateException("Could not run: " + String.join(" ", command), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while running: "
                    + String.join(" ", command), e);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for MySQL", e);
        }
    }

    private static void stopQuietly() {
        if (container == null) {
            return;
        }
        try {
            runDocker("rm", "-f", container);
        } catch (RuntimeException e) {
            LOGGER.warning(() -> "Could not remove the UI-test MySQL container: " + e.getMessage());
        }
    }
}
