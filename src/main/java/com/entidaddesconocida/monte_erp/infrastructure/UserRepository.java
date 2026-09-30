package com.entidaddesconocida.monte_erp.infrastructure;

import com.entidaddesconocida.monte_erp.infrastructure.config.DatabaseConnection;
import com.entidaddesconocida.monte_erp.domain.model.Usuario;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Acceso a datos de la tabla {@code USUARIO}.
 *
 * <p>Sin lógica de negocio: solo traduce entre {@link Usuario} y las filas de la
 * tabla. La baja de usuarios es lógica ({@link #desactivar(int)}), tal y como
 * exige el diagrama E-R.</p>
 */
public class UserRepository {

    private static final String COLUMNS = "id, nombre, usuario, password_hash, rol, activo";
    private static final String SELECT_ALL = "SELECT " + COLUMNS + " FROM USUARIO";

    /**
     * Busca un usuario por su login (campo único {@code usuario}).
     *
     * @return el usuario encontrado o {@code null} si no existe
     */
    public Usuario buscarPorUsuario(String usuario) throws SQLException {
        String sql = SELECT_ALL + " WHERE usuario = ?";
        try (PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement(sql)) {
            ps.setString(1, usuario);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /** Devuelve todos los usuarios ordenados por login. */
    public List<Usuario> listar() throws SQLException {
        List<Usuario> usuarios = new ArrayList<>();
        try (Statement statement = DatabaseConnection.getConnection().createStatement();
             ResultSet rs = statement.executeQuery(SELECT_ALL + " ORDER BY usuario")) {
            while (rs.next()) {
                usuarios.add(map(rs));
            }
        }
        return usuarios;
    }

    /**
     * Inserta un usuario nuevo (alta).
     *
     * @return el identificador generado, que además se asigna al objeto
     */
    public int insertar(Usuario usuario) throws SQLException {
        String sql = "INSERT INTO USUARIO (nombre, usuario, password_hash, rol, activo) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = DatabaseConnection.getConnection()
                .prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, usuario.getNombre());
            ps.setString(2, usuario.getUsuario());
            ps.setString(3, usuario.getPasswordHash());
            ps.setString(4, usuario.getRol());
            ps.setBoolean(5, usuario.isActivo());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                int id = keys.next() ? keys.getInt(1) : 0;
                usuario.setId(id);
                return id;
            }
        }
    }

    /** Actualiza todos los campos modificables de un usuario existente (edición). */
    public void actualizar(Usuario usuario) throws SQLException {
        String sql = "UPDATE USUARIO SET nombre = ?, usuario = ?, password_hash = ?, rol = ?, activo = ? WHERE id = ?";
        try (PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement(sql)) {
            ps.setString(1, usuario.getNombre());
            ps.setString(2, usuario.getUsuario());
            ps.setString(3, usuario.getPasswordHash());
            ps.setString(4, usuario.getRol());
            ps.setBoolean(5, usuario.isActivo());
            ps.setInt(6, usuario.getId());
            ps.executeUpdate();
        }
    }

    /**
     * Baja lógica del usuario: pasa {@code activo} a 0 sin borrar la fila, para
     * no romper las claves foráneas de compras y pedidos ya registrados.
     */
    public void desactivar(int id) throws SQLException {
        try (PreparedStatement ps = DatabaseConnection.getConnection()
                .prepareStatement("UPDATE USUARIO SET activo = 0 WHERE id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    private Usuario map(ResultSet rs) throws SQLException {
        Usuario usuario = new Usuario();
        usuario.setId(rs.getInt("id"));
        usuario.setNombre(rs.getString("nombre"));
        usuario.setUsuario(rs.getString("usuario"));
        usuario.setPasswordHash(rs.getString("password_hash"));
        usuario.setRol(rs.getString("rol"));
        usuario.setActivo(rs.getBoolean("activo"));
        return usuario;
    }
}
