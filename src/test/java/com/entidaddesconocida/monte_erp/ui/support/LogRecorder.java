package com.entidaddesconocida.monte_erp.ui.support;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Captures the {@code SEVERE} records emitted while a UI flow runs, so a test
 * fails when a screen logs an unexpected exception even if the visible labels
 * still look fine.
 */
public final class LogRecorder implements AutoCloseable {

    private final Logger root = Logger.getLogger("");
    private final List<LogRecord> severe = new CopyOnWriteArrayList<>();
    private final Handler handler = new Handler() {
        @Override
        public void publish(LogRecord record) {
            if (record.getLevel().intValue() >= Level.SEVERE.intValue()) {
                severe.add(record);
            }
        }

        @Override
        public void flush() {
            // Nothing to flush.
        }

        @Override
        public void close() {
            // Nothing to close.
        }
    };

    public LogRecorder() {
        handler.setLevel(Level.ALL);
        root.addHandler(handler);
    }

    public void assertNoSevere() {
        if (severe.isEmpty()) {
            return;
        }
        List<String> messages = severe.stream()
                .map(record -> record.getLevel() + " " + record.getLoggerName() + ": "
                        + record.getMessage())
                .toList();
        throw new AssertionError("Unexpected SEVERE log records: " + messages);
    }

    @Override
    public void close() {
        root.removeHandler(handler);
    }
}
