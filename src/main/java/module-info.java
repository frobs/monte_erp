/**
 * Módulo principal de Monte ERP.
 *
 * <p>Notas de módulos:</p>
 * <ul>
 *   <li>{@code java.sql} es necesario para JDBC (conexión, DAO y migraciones).</li>
 *   <li>{@code mysql.connector.j} es el nombre real del módulo del driver de
 *       MySQL: el JAR {@code mysql-connector-j} no incluye {@code module-info.class}
 *       ni {@code Automatic-Module-Name}, así que el nombre se deriva del nombre
 *       del artefacto. (El plan mencionaba {@code com.mysql.cj}, que es solo el
 *       {@code Bundle-SymbolicName} de OSGi del propio JAR.)</li>
 *   <li>El paquete {@code controller} se abre a {@code javafx.fxml} para que el
 *       {@code FXMLLoader} pueda inyectar los campos {@code @FXML}.</li>
 * </ul>
 */
module com.entidaddesconocida.monte_erp {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires net.synedra.validatorfx;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.bootstrapfx.core;
    requires eu.hansolo.tilesfx;

    requires java.sql;
    requires mysql.connector.j;

    opens com.entidaddesconocida.monte_erp.controller to javafx.fxml;
    exports com.entidaddesconocida.monte_erp;
}
