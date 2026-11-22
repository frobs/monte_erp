package com.entidaddesconocida.monte_erp.app;

import java.sql.SQLException;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Session check every screen runs before touching data: revalidate the signed-in
 * account and, when the session is over, clear the screen and report it.
 *
 * <p>The same {@code refresh() → clear state → fail(...)} block was copied in
 * five ViewModels (A4). This class keeps the sequence in one place and asks the
 * caller only for the two things that really differ: how to drop its own state
 * and how to show a message.</p>
 *
 * <p>Two failures are distinguished on purpose. A <strong>lost session</strong>
 * (absent or deactivated account) always drops the loaded data: it must not stay
 * on screen once the user cannot act on it. A <strong>database failure</strong>
 * while revalidating leaves the state alone by default — the user keeps the form
 * they were filling and can retry — unless the screen opts into clearing, which
 * is what the two read-only reports do.</p>
 *
 * <p>A {@code null} validator means "no session policy" and is accepted on
 * purpose: the unit tests build most ViewModels by hand.</p>
 */
public final class SessionGuard {

    private static final String REVALIDATION_FAILURE = "Could not revalidate the signed-in user";

    private final Logger logger;
    private final SessionValidator validator;
    private final String databaseError;
    private final String sessionExpired;

    /**
     * @param logger           where the {@link SQLException} of the revalidation
     *                         is logged, so the trace keeps the screen that hit it
     * @param validator        session revalidation, or {@code null} in the tests
     *                         that build the ViewModel without a session
     * @param databaseError    message shown when the account could not be read
     * @param sessionExpired   message shown when the account no longer exists or
     *                         was deactivated
     */
    public SessionGuard(Logger logger, SessionValidator validator,
                        String databaseError, String sessionExpired) {
        this.logger = logger;
        this.validator = validator;
        this.databaseError = databaseError;
        this.sessionExpired = sessionExpired;
    }

    /**
     * Revalidates the session, dropping the screen state when it is over.
     *
     * @param clearState drops the data of the screen; it is only run when the
     *                   session is lost, so a transient database failure does not
     *                   throw away a form the user is still filling
     * @param fail       shows the message; the screen decides whether it is the
     *                   error summary or the feedback of the whole view
     * @return {@code true} when the session is still active and the action may run
     */
    public boolean requireActive(Runnable clearState, Consumer<String> fail) {
        return requireActive(clearState, () -> { }, fail);
    }

    /**
     * Variant for the screens whose data must also be dropped when the session
     * cannot be revalidated (a read-only report must not survive a database
     * failure during the check).
     *
     * @param onSessionLost     runs when the account is gone or deactivated
     * @param onDatabaseFailure runs when the account could not be read
     * @param fail              shows the message of the failure
     * @return {@code true} when the session is still active and the action may run
     */
    public boolean requireActive(Runnable onSessionLost, Runnable onDatabaseFailure,
                                 Consumer<String> fail) {
        if (validator == null) {
            return true;
        }
        try {
            if (validator.refresh()) {
                return true;
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, REVALIDATION_FAILURE, e);
            onDatabaseFailure.run();
            fail.accept(databaseError);
            return false;
        }
        onSessionLost.run();
        fail.accept(sessionExpired);
        return false;
    }
}
