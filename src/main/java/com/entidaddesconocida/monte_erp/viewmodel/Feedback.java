package com.entidaddesconocida.monte_erp.viewmodel;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * The two message labels every screen shows: the error summary and the status
 * line.
 *
 * <p>Extracted from the master-detail ViewModels (A2) because the collaborators
 * of a screen — the draft editor, the history filter and the status changer —
 * report through the same pair. It keeps the rule the screens already had: only
 * one of the two is visible at a time, so {@link #fail(String)} clears the
 * status message and {@link #succeed(String)} clears the error.</p>
 */
public final class Feedback {

    private final StringProperty errorMessage = new SimpleStringProperty("");
    private final StringProperty statusMessage = new SimpleStringProperty("");

    public StringProperty errorMessageProperty() {
        return errorMessage;
    }

    public StringProperty statusMessageProperty() {
        return statusMessage;
    }

    /** Current error summary, empty when there is none. */
    public String error() {
        return errorMessage.get();
    }

    /** Shows {@code message} as the error summary and clears the status message. */
    public void fail(String message) {
        statusMessage.set("");
        errorMessage.set(message);
    }

    /** Shows {@code message} as the status message and clears the error summary. */
    public void succeed(String message) {
        errorMessage.set("");
        statusMessage.set(message);
    }

    /**
     * Shows {@code message} as the status message leaving the error summary
     * alone. Only used by the status pipeline when the write is committed but
     * the reload already reported a problem.
     */
    public void status(String message) {
        statusMessage.set(message);
    }

    /** Drops both messages, before a fresh interaction. */
    public void clear() {
        errorMessage.set("");
        statusMessage.set("");
    }

    /** Drops the error summary only, e.g. once the rejected filter is corrected. */
    public void clearError() {
        errorMessage.set("");
    }
}
