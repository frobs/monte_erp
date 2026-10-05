package com.entidaddesconocida.monte_erp.app;

import de.saxsys.mvvmfx.ViewTuple;

import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Utility to switch the {@link Stage} scene from a loaded mvvmFX {@link ViewTuple}.
 *
 * <p>Constants keep the window sizes in a single place.</p>
 */
public final class SceneNavigator {

    public static final double LOGIN_WIDTH = 460;
    public static final double LOGIN_HEIGHT = 480;
    public static final double SETUP_WIDTH = 560;
    public static final double SETUP_HEIGHT = 660;
    public static final double MAIN_WIDTH = 1000;
    public static final double MAIN_HEIGHT = 660;

    private SceneNavigator() {
        // Utility class: never instantiated.
    }

    /**
     * Replaces the stage scene with the view of the given tuple and adjusts
     * the title and size.
     */
    public static void show(Stage stage, ViewTuple<?, ?> tuple, String title,
                            double width, double height) {
        stage.setTitle(title);
        stage.setScene(new Scene(tuple.getView(), width, height));
        stage.centerOnScreen();
    }
}
