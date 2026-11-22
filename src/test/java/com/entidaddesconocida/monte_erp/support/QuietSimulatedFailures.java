package com.entidaddesconocida.monte_erp.support;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.sql.SQLException;
import java.util.logging.Filter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Keeps the stack traces of the in-memory repositories out of the build output.
 *
 * <p>The view-model tests deliberately make a fake repository fail
 * ({@code throwOnAccess()}, {@code failOn("update")}...) and assert the message
 * the screen shows. The production code logs those failures with their stack
 * trace, which is correct, but in the tests the trace is expected noise: the
 * assertion already proves the failure was handled. The duplicate-key tests do
 * the same: they force a unique-key clash to check that the screen turns it into
 * a field error, and the production code correctly logs it as a {@code WARNING}
 * with its stack trace.</p>
 *
 * <p>This extension drops only the records whose exception is one of those
 * expected failures; any other exception still reaches the console and
 * {@code LogRecorder} (which reads its own handler, so it is unaffected).</p>
 *
 * <p>It is auto-detected through {@code junit-platform.properties} and the
 * {@code META-INF/services} file, so every test gets it without repeating an
 * annotation.</p>
 */
public final class QuietSimulatedFailures implements BeforeAllCallback {

    private static final String MARKER = "Simulated database failure";

    /** Message the fakes (and the real schema) raise on a unique-key clash. */
    private static final String DUPLICATE_KEY_MARKER = "Duplicate entry";

    private static boolean installed;

    @Override
    public void beforeAll(ExtensionContext context) {
        install();
    }

    private static synchronized void install() {
        if (installed) {
            return;
        }
        for (Handler handler : Logger.getLogger("").getHandlers()) {
            Filter previous = handler.getFilter();
            handler.setFilter(record -> (previous == null || previous.isLoggable(record))
                    && !isSimulated(record));
        }
        installed = true;
    }

    private static boolean isSimulated(LogRecord record) {
        for (Throwable thrown = record.getThrown(); thrown != null; thrown = thrown.getCause()) {
            if (thrown instanceof SQLException && isSimulatedMessage(thrown.getMessage())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSimulatedMessage(String message) {
        return message != null
                && (message.startsWith(MARKER) || message.startsWith(DUPLICATE_KEY_MARKER));
    }
}
