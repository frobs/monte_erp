package com.entidaddesconocida.monte_erp.app;

import de.saxsys.mvvmfx.ViewTuple;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;

/**
 * Utility to switch the {@link Stage} scene from a loaded mvvmFX {@link ViewTuple}.
 *
 * <p>Constants keep the window sizes in a single place. The application always
 * opens full screen (the stage is maximized on the first screen) and stays
 * resizable, so the sizes below are only the window size used while the stage is
 * not maximized.</p>
 */
public final class SceneNavigator {

    public static final double LOGIN_WIDTH = 460;
    public static final double LOGIN_HEIGHT = 480;
    public static final double SETUP_WIDTH = 560;
    public static final double SETUP_HEIGHT = 660;
    public static final double MAIN_WIDTH = 1000;
    public static final double MAIN_HEIGHT = 660;
    public static final double USERS_WIDTH = 1040;
    public static final double USERS_HEIGHT = 680;
    public static final double PRODUCTS_WIDTH = 1180;
    public static final double PRODUCTS_HEIGHT = 700;
    public static final double CATEGORIES_WIDTH = 1100;
    public static final double CATEGORIES_HEIGHT = 680;
    public static final double SUPPLIERS_WIDTH = 1100;
    public static final double SUPPLIERS_HEIGHT = 680;
    public static final double CUSTOMERS_WIDTH = 1100;
    public static final double CUSTOMERS_HEIGHT = 680;
    public static final double SALES_ORDERS_WIDTH = 1180;
    public static final double SALES_ORDERS_HEIGHT = 720;
    public static final double PURCHASES_WIDTH = 1180;
    public static final double PURCHASES_HEIGHT = 720;
    public static final double STOCK_WIDTH = 1180;
    public static final double STOCK_HEIGHT = 700;
    public static final double REPORTS_WIDTH = 1180;
    public static final double REPORTS_HEIGHT = 720;

    private SceneNavigator() {
        // Utility class: never instantiated.
    }

    /**
     * Replaces the stage scene with the view of the given tuple, adjusts the
     * title and guarantees the window is shown full screen.
     *
     * <p>The maximize is applied only the first time a scene is shown: later
     * navigations keep whatever size the user chose. The window is therefore
     * always resizable and the user decides whether to restore, move or resize
     * it; the application never forces the size back.</p>
     */
    public static void show(Stage stage, ViewTuple<?, ?> tuple, String title,
                            double width, double height) {
        show(stage, tuple, title, width, height, null);
    }

    /**
     * Replaces the stage scene and, when {@code onEscape} is not {@code null},
     * makes the ESC key run it (US-12: ESC goes back to the main menu).
     *
     * <p>The handler is registered at scene level and is ignored while the focus
     * is on a text input or a combo, so a half-typed form is never discarded. The
     * popups of the searchable combos and the {@code DatePicker} live in their own
     * scene, so closing them with ESC does not trigger the navigation.</p>
     *
     * <p>The maximize is applied only the first time a scene is shown: later
     * navigations keep whatever size the user chose. The window is therefore
     * always resizable and the user decides whether to restore, move or resize
     * it; the application never forces the size back.</p>
     */
    public static void show(Stage stage, ViewTuple<?, ?> tuple, String title,
                            double width, double height, Runnable onEscape) {
        Scene scene = new Scene(tuple.getView(), width, height);
        installEscapeHandler(scene, onEscape);
        stage.setTitle(title);
        stage.setScene(scene);
        if (!stage.isShowing()) {
            // Full screen by default, but never a non-resizable window: if the user
            // restores or resizes it, the choice is respected from then on.
            stage.setResizable(true);
            stage.setMaximized(true);
        }
    }

    /** Runs {@code onEscape} on ESC unless the user is editing a field. */
    private static void installEscapeHandler(Scene scene, Runnable onEscape) {
        if (onEscape == null) {
            return;
        }
        scene.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() != KeyCode.ESCAPE || isEditing(scene.getFocusOwner())) {
                return;
            }
            onEscape.run();
            event.consume();
        });
    }

    /** A text field or a combo keeps the ESC key for itself while it has the focus. */
    private static boolean isEditing(Node focusOwner) {
        return focusOwner instanceof TextInputControl || focusOwner instanceof ComboBoxBase;
    }
}
