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
    @DisplayName("The V1 script is discovered on the classpath (exploded or inside a JAR)")
    void discoversScripts() throws Exception {
        List<String> scripts = MigrationRunner.discoverScripts();
        assertTrue(scripts.contains("V1__schema.sql"), "scripts found: " + scripts);
    }
}
