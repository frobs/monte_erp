package com.entidaddesconocida.monte_erp.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the migration engine: script splitting, version names and discovery
 * of the {@code .sql} files.
 */
class MigrationRunnerTest {

    @Test
    @DisplayName("Statements are split on ';' while ignoring comments")
    void splitsStatements() {
        String script = """
                -- header comment
                CREATE TABLE A (id INT);
                /* multi
                   line comment */
                INSERT INTO A (id) VALUES (1);
                INSERT INTO A (id) VALUES (2) # trailing comment
                """;
        List<String> statements = MigrationRunner.splitStatements(script);
        assertEquals(3, statements.size(), statements.toString());
        assertTrue(statements.get(0).startsWith("CREATE TABLE A"));
        assertTrue(statements.get(2).startsWith("INSERT INTO A (id) VALUES (2)"));
    }

    @Test
    @DisplayName("A ';' inside a literal does not split statements")
    void respectsLiterals() {
        List<String> statements = MigrationRunner.splitStatements(
                "INSERT INTO A (t) VALUES ('one;two'); SELECT 'it''s ok';");
        assertEquals(2, statements.size(), statements.toString());
        assertEquals("INSERT INTO A (t) VALUES ('one;two')", statements.get(0));
        assertEquals("SELECT 'it''s ok'", statements.get(1));
    }

    @Test
    @DisplayName("The last statement is collected even without a trailing ';'")
    void lastStatementWithoutSemicolon() {
        assertEquals(1, MigrationRunner.splitStatements("SELECT 1").size());
        assertEquals(0, MigrationRunner.splitStatements("-- only a comment").size());
    }

    @Test
    @DisplayName("versionOf() extracts the version from the V<n>__<name>.sql convention")
    void extractsVersion() {
        assertEquals("V1", MigrationRunner.versionOf("V1__schema.sql"));
        assertEquals("V10", MigrationRunner.versionOf("V10__sales_orders.sql"));
        assertEquals("V2", MigrationRunner.versionOf("V2__seed_data.sql"));
    }

    @Test
    @DisplayName("versionOf() tolera nombres sin separador y la extensión en mayúsculas")
    void extractsVersionEdgeCases() {
        assertEquals("V3", MigrationRunner.versionOf("V3__a.SQL"));
        assertEquals("V2", MigrationRunner.versionOf("V2"));
        assertEquals("schema", MigrationRunner.versionOf("schema"));
        assertEquals("__leading", MigrationRunner.versionOf("__leading.sql"));
    }

    @Test
    @DisplayName("A ';' inside a double-quoted identifier does not split statements")
    void respectsDoubleQuotedLiterals() {
        List<String> statements = MigrationRunner.splitStatements(
                "INSERT INTO A (t) VALUES (\"one;two\"); SELECT 1;");
        assertEquals(2, statements.size(), statements.toString());
        assertEquals("INSERT INTO A (t) VALUES (\"one;two\")", statements.get(0));
        assertEquals("SELECT 1", statements.get(1));
    }

    @Test
    @DisplayName("Block comments and '#' comments are ignored until the end of the script")
    void ignoresTrailingComments() {
        assertEquals(1, MigrationRunner.splitStatements("SELECT 1; /* trailing block").size());
        assertEquals(1, MigrationRunner.splitStatements("SELECT 2 # trailing line").size());
    }

    @Test
    @DisplayName("The V1, V4 and V8 scripts are discovered on the classpath (exploded or inside a JAR)")
    void discoversScripts() throws Exception {
        List<String> scripts = MigrationRunner.discoverScripts();
        assertTrue(scripts.contains("V1__schema.sql"), "scripts found: " + scripts);
        // CATEGORY.active comes from V4: if that resource disappears from the JAR,
        // every existing installation breaks at start-up and no test would notice.
        assertTrue(scripts.contains("V4__category_active.sql"), "scripts found: " + scripts);
        // The purchase status pipeline needs the V8 ENUM: without it, saving a
        // returned purchase fails at runtime on an up-to-date database.
        assertTrue(scripts.contains("V8__purchase_status.sql"), "scripts found: " + scripts);
    }
}
