package com.entidaddesconocida.monte_erp.viewmodel;

/**
 * Flag that keeps a second write from being submitted while the first one is
 * still running in the background.
 *
 * <p>While the writes ran on the JavaFX thread a double click was impossible;
 * now that they are asynchronous, the screen has to remember that one is in
 * flight. The same flag guards a save and a status change of the same screen, so
 * a transition cannot be submitted while the document is being persisted (and
 * the other way round).</p>
 *
 * <p>Extracted from the master-detail ViewModels (A2); it replaces the
 * {@code writeInFlight} boolean they repeated.</p>
 */
public final class WriteGuard {

    private boolean inFlight;

    /**
     * Marks a write as started.
     *
     * @return {@code true} when the caller may submit it; {@code false} when
     *         another write is still in flight and this one must be dropped
     */
    public boolean tryBegin() {
        if (inFlight) {
            return false;
        }
        inFlight = true;
        return true;
    }

    /** Releases the flag, from both the success and the failure callback. */
    public void end() {
        inFlight = false;
    }

    /** Whether a write is currently in flight. */
    public boolean isBusy() {
        return inFlight;
    }
}
