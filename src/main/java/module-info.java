/**
 * Main module of Monte ERP.
 *
 * <p>Module notes:</p>
 * <ul>
 *   <li>{@code java.sql} is required for JDBC (connection, repository and
 *       migrations).</li>
 *   <li>{@code mysql.connector.j} is the real module name of the MySQL driver:
 *       the {@code mysql-connector-j} JAR ships neither {@code module-info.class}
 *       nor {@code Automatic-Module-Name}, so the name is derived from the
 *       artifact name. (The plan mentioned {@code com.mysql.cj}, which is only
 *       the OSGi {@code Bundle-SymbolicName} of that JAR.)</li>
 *   <li>The {@code controller} package is opened to {@code javafx.fxml} so the
 *       {@code FXMLLoader} can inject the {@code @FXML} fields.</li>
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
