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
 *   <li>{@code de.saxsys.mvvmfx} is an <em>automatic module</em> (the JAR carries
 *       {@code Automatic-Module-Name: de.saxsys.mvvmfx} but no
 *       {@code module-info.class}). Related artefacts with a hyphen in their
 *       automatic name ({@code mvvmfx-validation}, {@code mvvmfx-easydi}) are
 *       <strong>not usable on the module path</strong> because a hyphen is not a
 *       valid Java identifier, so they are deliberately excluded from the POM.</li>
 *   <li>The {@code view} package is opened to {@code javafx.fxml} (so the
 *       {@code FXMLLoader} can inject {@code @FXML} fields) and to
 *       {@code de.saxsys.mvvmfx} (so mvvmFX can instantiate the View code-behind
 *       and locate the companion {@code .fxml} resource).</li>
 *   <li>The {@code viewmodel} package is opened to {@code de.saxsys.mvvmfx} so
 *       the framework can instantiate ViewModels and inject {@code @InjectViewModel}.</li>
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
    requires java.keyring;

    requires de.saxsys.mvvmfx;

    opens com.entidaddesconocida.monte_erp.view to de.saxsys.mvvmfx, javafx.fxml;
    opens com.entidaddesconocida.monte_erp.viewmodel to de.saxsys.mvvmfx;

    exports com.entidaddesconocida.monte_erp;
}
