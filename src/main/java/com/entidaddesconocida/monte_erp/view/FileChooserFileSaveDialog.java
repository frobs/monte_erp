package com.entidaddesconocida.monte_erp.view;

import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;

/**
 * Production {@link FileSaveDialog}: the native JavaFX "save as" chooser.
 */
public final class FileChooserFileSaveDialog implements FileSaveDialog {

    @Override
    public File choose(Window owner, String title, String initialFileName,
                       String description, String extension) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.setInitialFileName(initialFileName);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(description, extension));
        return chooser.showSaveDialog(owner);
    }
}
