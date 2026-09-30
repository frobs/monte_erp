package com.entidaddesconocida.monte_erp.domain.model;

/**
 * Usuario del sistema (tabla {@code USUARIO} del diagrama E-R).
 *
 * <p>La contraseña nunca se guarda en claro: {@link #passwordHash} contiene el
 * hash PBKDF2 generado por {@code security.PasswordHasher}. La baja de usuarios
 * es lógica, mediante {@link #activo}.</p>
 */
public class Usuario {

    /** Rol con permisos de administración. */
    public static final String ROL_ADMIN = "admin";

    /** Rol de empleado (permisos básicos). */
    public static final String ROL_EMPLEADO = "empleado";

    private int id;
    private String nombre;
    private String usuario;
    private String passwordHash;
    private String rol;
    private boolean activo;

    public Usuario() {
    }

    public Usuario(String nombre, String usuario, String passwordHash, String rol, boolean activo) {
        this.nombre = nombre;
        this.usuario = usuario;
        this.passwordHash = passwordHash;
        this.rol = rol;
        this.activo = activo;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getUsuario() {
        return usuario;
    }

    public void setUsuario(String usuario) {
        this.usuario = usuario;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getRol() {
        return rol;
    }

    public void setRol(String rol) {
        this.rol = rol;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    /** {@code true} si el usuario tiene rol de administrador. */
    public boolean esAdmin() {
        return ROL_ADMIN.equals(rol);
    }

    /** Etiqueta legible del rol, para mostrar en la interfaz. */
    public String getRolEtiqueta() {
        return esAdmin() ? "Administrador" : "Empleado";
    }

    @Override
    public String toString() {
        return "Usuario{id=" + id + ", usuario='" + usuario + "', rol='" + rol + "', activo=" + activo + '}';
    }
}
