package com.entidaddesconocida.monte_erp.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del motor de migraciones: división de scripts, nombres de versión y
 * descubrimiento de los ficheros {@code .sql}.
 */
class MigrationRunnerTest {

    @Test
    @DisplayName("Las sentencias se separan por ';' ignorando comentarios")
    void divideSentencias() {
        String script = """
                -- comentario de cabecera
                CREATE TABLE A (id INT);
                /* comentario
                   multilínea */
                INSERT INTO A (id) VALUES (1);
                INSERT INTO A (id) VALUES (2) # comentario al final
                """;
        List<String> statements = MigrationRunner.splitStatements(script);
        assertEquals(3, statements.size(), statements.toString());
        assertTrue(statements.get(0).startsWith("CREATE TABLE A"));
        assertTrue(statements.get(2).startsWith("INSERT INTO A (id) VALUES (2)"));
    }

    @Test
    @DisplayName("Un ';' dentro de un literal no separa sentencias")
    void respetaLiterales() {
        List<String> statements = MigrationRunner.splitStatements(
                "INSERT INTO A (t) VALUES ('uno;dos'); SELECT 'it''s ok';");
        assertEquals(2, statements.size(), statements.toString());
        assertEquals("INSERT INTO A (t) VALUES ('uno;dos')", statements.get(0));
        assertEquals("SELECT 'it''s ok'", statements.get(1));
    }

    @Test
    @DisplayName("La última sentencia se recoge aunque no termine en ';'")
    void ultimaSentenciaSinPuntoYComa() {
        assertEquals(1, MigrationRunner.splitStatements("SELECT 1").size());
        assertEquals(0, MigrationRunner.splitStatements("-- solo un comentario").size());
    }

    @Test
    @DisplayName("versionOf() extrae la versión de la convención V<n>__<nombre>.sql")
    void extraeVersion() {
        assertEquals("V1", MigrationRunner.versionOf("V1__schema.sql"));
        assertEquals("V10", MigrationRunner.versionOf("V10__pedidos.sql"));
        assertEquals("V2", MigrationRunner.versionOf("V2__datos.sql"));
    }

    @Test
    @DisplayName("Se descubre el script V1 del classpath (explotado o dentro de un JAR)")
    void descubreScripts() throws Exception {
        List<String> scripts = MigrationRunner.discoverScripts();
        assertTrue(scripts.contains("V1__schema.sql"), "scripts encontrados: " + scripts);
    }
}
