package com.entidaddesconocida.monte_erp.infrastructure.config;

import com.entidaddesconocida.monte_erp.infrastructure.UserRepository;
import com.entidaddesconocida.monte_erp.domain.model.Usuario;
import com.entidaddesconocida.monte_erp.security.PasswordHasher;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Verifica contra la base de datos real (contenedor {@code monte_erp_mysql}) el
 * flujo de autenticación de RF-01: migraciones aplicadas, usuarios sembrados y
 * contraseñas verificables.
 *
 * <p>Si MySQL no está disponible, las pruebas se marcan como omitidas en lugar
 * de fallar: {@code ./mvnw test} debe poder ejecutarse sin la base de datos
 * arrancada.</p>
 */
class DataSeederTest {

    private static final String LOGIN_TEMPORAL = "test_rf01_usuario";

    @BeforeAll
    static void prepararBaseDeDatos() {
        assumeTrue(baseDeDatosDisponible(), "MySQL no disponible: pruebas de integración omitidas");
        try {
            MigrationRunner.migrate();
            DataSeeder.seed();
        } catch (Exception e) {
            assumeTrue(false, "No se pudo preparar la base de datos: " + e.getMessage());
        }
    }

    @AfterEach
    void limpiarUsuarioTemporal() throws SQLException {
        try (PreparedStatement ps = DatabaseConnection.getConnection()
                .prepareStatement("DELETE FROM USUARIO WHERE usuario = ?")) {
            ps.setString(1, LOGIN_TEMPORAL);
            ps.executeUpdate();
        }
    }

    @Test
    @DisplayName("admin/admin123 y empleado/empleado123 inician sesión con su rol")
    void usuariosSembradosPuedenIniciarSesion() throws SQLException {
        UserRepository userRepository = new UserRepository();

        Usuario admin = userRepository.buscarPorUsuario(DataSeeder.ADMIN_USUARIO);
        assertNotNull(admin, "El seed debe crear el usuario admin");
        assertTrue(PasswordHasher.verify(DataSeeder.ADMIN_PASSWORD, admin.getPasswordHash()));
        assertTrue(admin.esAdmin());
        assertTrue(admin.isActivo());

        Usuario empleado = userRepository.buscarPorUsuario(DataSeeder.EMPLEADO_USUARIO);
        assertNotNull(empleado, "El seed debe crear el usuario empleado");
        assertTrue(PasswordHasher.verify(DataSeeder.EMPLEADO_PASSWORD, empleado.getPasswordHash()));
        assertFalse(empleado.esAdmin());
        assertEquals(Usuario.ROL_EMPLEADO, empleado.getRol());
    }

    @Test
    @DisplayName("La contraseña errónea se rechaza y el hash no está en claro")
    void contrasenaErroneaNoAutentica() throws SQLException {
        Usuario admin = new UserRepository().buscarPorUsuario(DataSeeder.ADMIN_USUARIO);
        assertNotNull(admin);
        assertFalse(PasswordHasher.verify("contrasena-incorrecta", admin.getPasswordHash()));
        assertFalse(admin.getPasswordHash().contains(DataSeeder.ADMIN_PASSWORD),
                "password_hash no debe contener la contraseña en claro");
    }

    @Test
    @DisplayName("Un usuario inexistente devuelve null")
    void usuarioInexistente() throws SQLException {
        assertNull(new UserRepository().buscarPorUsuario("no_existe_este_usuario"));
    }

    @Test
    @DisplayName("Un usuario desactivado conserva su hash pero queda inactivo (baja lógica)")
    void usuarioDesactivadoNoPuedeIniciarSesion() throws SQLException {
        UserRepository userRepository = new UserRepository();

        Usuario temporal = new Usuario("Usuario de prueba", LOGIN_TEMPORAL,
                PasswordHasher.hash("temporal123"), Usuario.ROL_EMPLEADO, true);
        userRepository.insertar(temporal);
        assertTrue(temporal.getId() > 0, "El alta debe devolver el id generado");

        Usuario recuperado = userRepository.buscarPorUsuario(LOGIN_TEMPORAL);
        assertNotNull(recuperado);
        assertTrue(recuperado.isActivo());

        userRepository.desactivar(recuperado.getId());

        Usuario desactivado = userRepository.buscarPorUsuario(LOGIN_TEMPORAL);
        assertNotNull(desactivado, "La baja es lógica: la fila no se borra");
        assertFalse(desactivado.isActivo(), "Tras desactivar, activo debe ser 0");
        assertFalse(desactivado.isActivo() && PasswordHasher.verify("temporal123", desactivado.getPasswordHash()),
                "El login debe rechazarse para un usuario desactivado");
    }

    private static boolean baseDeDatosDisponible() {
        try {
            DatabaseConnection.getConnection();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
