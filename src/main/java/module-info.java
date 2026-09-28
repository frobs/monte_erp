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

    opens com.entidaddesconocida.monte_erp to javafx.fxml;
    exports com.entidaddesconocida.monte_erp;
}