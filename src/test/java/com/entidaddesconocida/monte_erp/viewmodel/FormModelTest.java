package com.entidaddesconocida.monte_erp.viewmodel;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the {@link FormModel} collaborator used by the composition path
 * (A3b): joining/first summarization, the validate-on-edit lifecycle, the live
 * sources and the always-on field validation.
 */
class FormModelTest {

    private Feedback feedback;
    private StringProperty name;
    private StringProperty phone;
    private ReadOnlyStringWrapper nameError;
    private ReadOnlyStringWrapper phoneError;
    private FormModel<String> form;

    @BeforeEach
    void setUp() {
        feedback = new Feedback();
        name = new SimpleStringProperty("");
        phone = new SimpleStringProperty("");
        nameError = new ReadOnlyStringWrapper("");
        phoneError = new ReadOnlyStringWrapper("");
        form = new FormModel<>(feedback,
                value -> name.set(value),
                () -> name.set(""));
        form.field(nameError, this::validateName)
                .field(phoneError, this::validatePhone)
                .watch(name, phone);
    }

    @Test
    @DisplayName("la política JOINED une los mensajes con saltos de línea")
    void joinedSummaryContainsEveryMessage() {
        form.refresh();

        assertEquals("Introduce el nombre.", form.summary());
        name.set("Algo");
        form.refresh();
        assertEquals("", form.summary());

        phone.set("no-es-un-telefono");
        form.refresh();
        assertEquals("El teléfono no es válido.", form.summary());
    }

    @Test
    @DisplayName("la política FIRST devuelve solo el primer mensaje no vacío")
    void firstPolicyReturnsTheFirstMessage() {
        form.summaryPolicy(FormModel.SummaryPolicy.FIRST);
        form.refresh();

        assertEquals("Introduce el nombre.", form.summary());

        name.set("Algo");
        phone.set("no-es-un-telefono");
        form.refresh();
        assertEquals("El teléfono no es válido.", form.summary());
    }

    @Test
    @DisplayName("populate() limpia los errores y desactiva la validación en vivo")
    void populateResetsErrorsAndFlag() {
        form.startValidation();
        form.refresh();
        assertTrue(form.hasErrors());

        form.populate("Cargado");

        assertEquals("Cargado", name.get());
        assertFalse(form.hasErrors());
        assertEquals("", nameError.get());
        assertFalse(form.isValidateOnEdit());
    }

    @Test
    @DisplayName("los campos solo se revalidan en vivo tras el primer intento de guardado")
    void liveValidationStartsAfterTheFirstSaveAttempt() {
        phone.set("no-es-un-telefono");
        assertEquals("", feedback.error());

        form.startValidation();
        phone.set("123456789");
        assertEquals("Introduce el nombre.", feedback.error());

        name.set("Algo");
        assertEquals("", feedback.error());
    }

    @Test
    @DisplayName("un campo con validación permanente avisa y no borra un error ajeno")
    void liveFieldReportsAndKeepsForeignErrors() {
        ReadOnlyStringWrapper liveError = new ReadOnlyStringWrapper("");
        form.watchLive(phone, this::validatePhone, liveError);

        phone.set("no-es-un-telefono");
        assertEquals("El teléfono no es válido.", feedback.error());
        assertEquals("El teléfono no es válido.", liveError.get());

        feedback.fail("No se pudo conectar con la base de datos.");
        phone.set("");
        assertEquals("No se pudo conectar con la base de datos.", feedback.error());
        assertEquals("", liveError.get());
    }

    private String validateName() {
        return name.get().trim().isEmpty() ? "Introduce el nombre." : "";
    }

    private String validatePhone() {
        if (phone.get().isBlank()) {
            return "";
        }
        return phone.get().matches("[0-9 ]{9,}") ? "" : "El teléfono no es válido.";
    }
}
