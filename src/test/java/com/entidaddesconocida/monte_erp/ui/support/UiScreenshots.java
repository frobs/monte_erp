package com.entidaddesconocida.monte_erp.ui.support;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.DialogPane;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.Window;

import javax.imageio.ImageIO;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Saves PNG screenshots of the real application window to illustrate the user
 * manual.
 *
 * <p>The image is a JavaFX snapshot of the scene (or of the modal dialog that is
 * open), not a screen grab of the X display: it has no window decorations, it
 * does not depend on the desktop resolution and it works with the software
 * renderer the tests use. The output directory is
 * {@code docs/manual_de_usuario/img} unless {@code -Dmanual.screenshots.dir} says
 * otherwise.</p>
 *
 * <p>Snapshots require the node to be attached to a shown scene and to have its
 * CSS applied, which is what the tests get after {@link UiActions#settle()}.</p>
 */
public final class UiScreenshots {

    private static final Path DEFAULT_DIRECTORY =
            Path.of(System.getProperty("manual.screenshots.dir", "docs/manual_de_usuario/img"));

    private final Stage stage;
    private final Path directory;
    private final List<Path> saved = new ArrayList<>();

    public UiScreenshots(Stage stage) {
        this.stage = stage;
        this.directory = DEFAULT_DIRECTORY;
    }

    /** Directory where the next screenshots are written. */
    public static Path directory() {
        return DEFAULT_DIRECTORY;
    }

    /** Captures the whole window of the application. */
    public void capture(String name) {
        save(name, sceneRoot());
    }

    /** Captures a single control or container of the window. */
    public void capture(String name, Node node) {
        save(name, node);
    }

    /**
     * Captures the modal dialog currently on screen (confirmations, warnings,
     * export notices). The dialog is drawn on top of the window by the platform,
     * so a snapshot of the scene would not include it.
     */
    public void captureDialog(String name) {
        Window dialog = FxRunner.get(() -> {
            for (Window window : Window.getWindows()) {
                if (!window.isShowing() || window == stage || window.getScene() == null) {
                    continue;
                }
                Parent root = window.getScene().getRoot();
                if (root instanceof DialogPane || root.lookup(".dialog-pane") != null) {
                    return window;
                }
            }
            return null;
        });
        if (dialog == null) {
            throw new AssertionError("No modal dialog on screen to capture as '" + name + "'");
        }
        save(name, FxRunner.get(() -> dialog.getScene().getRoot()));
    }

    /** Files written so far, in capture order. */
    public List<Path> saved() {
        return List.copyOf(saved);
    }

    private Parent sceneRoot() {
        Parent root = FxRunner.get(() -> {
            if (stage.getScene() == null) {
                return null;
            }
            return stage.getScene().getRoot();
        });
        if (root == null) {
            throw new AssertionError("The window has no scene to capture");
        }
        return root;
    }

    private void save(String name, Node node) {
        Image image = FxRunner.get(() -> snapshot(node));
        Path file = directory.resolve(name + ".png");
        try {
            Files.createDirectories(directory);
            if (!ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", file.toFile())) {
                throw new IllegalStateException("No PNG writer available for " + file);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write the screenshot " + file, e);
        }
        saved.add(file);
    }

    private static Image snapshot(Node node) {
        if (node instanceof Parent root) {
            root.applyCss();
            root.requestLayout();
            root.layout();
        }
        SnapshotParameters parameters = new SnapshotParameters();
        // The window background is white; keeping it avoids transparent pockets
        // in the areas the layout does not paint.
        parameters.setFill(Color.WHITE);
        return node.snapshot(parameters, null);
    }
}
