package com.entidaddesconocida.monte_erp.view;

import javafx.stage.Window;

import java.io.File;

/**
 * Asks the user where to save a report.
 *
 * <p>It exists so the View does not hard-code the {@link javafx.stage.FileChooser}
 * (a native modal dialog that cannot be driven from a UI test): the production
 * implementation is {@link FileChooserFileSaveDialog}, while the UI tests inject
 * a fake that returns a temporary file.</p>
 */
public interface FileSaveDialog {

    /**
     * Shows a "save as" dialog.
     *
     * @param owner           window the dialog belongs to
     * @param title           dialog title
     * @param initialFileName file name suggested to the user
     * @param description     human readable description of the file type
     * @param extension       extension filter, e.g. {@code "*.csv"}
     * @return the chosen file, or {@code null} when the user cancels
     */
    File choose(Window owner, String title, String initialFileName,
                String description, String extension);
}
