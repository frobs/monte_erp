package com.entidaddesconocida.monte_erp.ui.support;

import com.entidaddesconocida.monte_erp.view.FileSaveDialog;

import javafx.stage.Window;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link FileSaveDialog} double for the UI tests: it never opens a native
 * dialog, it returns the file configured by the test and records the calls.
 */
public final class FakeFileSaveDialog implements FileSaveDialog {

    private final List<String> titles = new ArrayList<>();
    private File nextFile;

    public FakeFileSaveDialog setNextFile(File file) {
        this.nextFile = file;
        return this;
    }

    public List<String> titles() {
        return titles;
    }

    public boolean wasUsed() {
        return !titles.isEmpty();
    }

    @Override
    public File choose(Window owner, String title, String initialFileName,
                       String description, String extension) {
        titles.add(title);
        return nextFile;
    }
}
