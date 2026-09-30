package com.entidaddesconocida.monte_erp.infrastructure.config;

import com.entidaddesconocida.monte_erp.infrastructure.UserRepository;
import com.entidaddesconocida.monte_erp.domain.model.Usuario;
import com.entidaddesconocida.monte_erp.security.PasswordHasher;

import java.sql.SQLException;

/**
 * Datos mínimos que la aplicación necesita para poder iniciar sesión.
 *
 * <p>Se hace en Java (y no en un script SQL) para que los hashes se generen con
 * el mismo {@link PasswordHasher} que después los verifica: un hash escrito a
 * mano sería imposible de generar sin el salt y podría quedar desincronizado.</p>
 *
 * <p>Es idempotente: solo inserta el usuario si su login no existe todavía, así
 * que arrancar la aplicación varias veces no duplica filas ni pisa contraseñas
 * que el administrador haya cambiado.</p>
 */
public final class DataSeeder {

    /** Credenciales de desarrollo, documentadas en el plan del proyecto. */
    public static final String ADMIN_USUARIO = "admin";
    public static final String ADMIN_PASSWORD = "admin123";
    public static final String EMPLEADO_USUARIO = "empleado";
    public static final String EMPLEADO_PASSWORD = "empleado123";

    private static final UserRepository USUARIO_DAO = new UserRepository();

    private DataSeeder() {
        // Utilidad: no se instancia.
    }

    /**
     * Crea los usuarios iniciales que falten.
     *
     * @return número de usuarios creados
     */
    public static int seed() throws SQLException {
        int creados = 0;
        creados += crearSiNoExiste("Administrador", ADMIN_USUARIO, ADMIN_PASSWORD, Usuario.ROL_ADMIN);
        creados += crearSiNoExiste("Empleado de mostrador", EMPLEADO_USUARIO, EMPLEADO_PASSWORD, Usuario.ROL_EMPLEADO);
        return creados;
    }

    private static int crearSiNoExiste(String nombre, String login, String password, String rol)
            throws SQLException {
        if (USUARIO_DAO.buscarPorUsuario(login) != null) {
            return 0;
        }
        Usuario usuario = new Usuario(nombre, login, PasswordHasher.hash(password), rol, true);
        USUARIO_DAO.insertar(usuario);
        return 1;
    }
}
