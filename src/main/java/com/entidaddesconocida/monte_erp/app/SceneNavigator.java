package com.entidaddesconocida.monte_erp.app;

import de.saxsys.mvvmfx.ViewTuple;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Screen;
import javafx.stage.Stage;

/**
 * Utility to switch the {@link Stage} contents to the root of a loaded mvvmFX
 * {@link ViewTuple}.
 *
 * <p>Constants keep the window sizes in a single place. The application always
 * opens full screen (the stage is maximized on the first screen) and stays
 * resizable, so the sizes below are only used for the very first scene, while
 * the stage is not maximized.</p>
 *
 * <p>A maximized window is restored to the bounds it had just before being
 * maximized, and the platform does it not only with the system button but also
 * whenever a modal dialog takes the focus. The stage therefore fills its screen
 * before maximizing: whatever restores it brings back the very same size, so
 * the confirmation dialogs never resize the window nor bring back the small
 * login/setup size.</p>
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

    /** Key of the scene owned by this navigator inside {@link Stage#getProperties()}. */
    private static final String SCENE_PROPERTY = "monte-erp.scene";
    /** Key of the action currently bound to ESC inside {@link Scene#getProperties()}. */
    private static final String ESCAPE_PROPERTY = "monte-erp.on-escape";

    private SceneNavigator() {
        // Utility class: never instantiated.
    }

    /**
     * Replaces the stage contents with the view of the given tuple and adjusts
     * the title.
     */
    public static void show(Stage stage, ViewTuple<?, ?> tuple, String title,
                            double width, double height) {
        show(stage, tuple, title, width, height, null);
    }

    /**
     * Replaces the stage contents and, when {@code onEscape} is not {@code null},
     * makes the ESC key run it (US-12: ESC goes back to the main menu).
     *
     * <p>The handler is registered at scene level and is ignored while the focus
     * is on a text input or a combo, so a half-typed form is never discarded. The
     * popups of the searchable combos and the {@code DatePicker} live in their own
     * scene, so closing them with ESC does not trigger the navigation.</p>
     *
     * <p>The stage keeps a single {@link Scene} for the whole session: navigating
     * only swaps its root, so the window is never resized (no shrink-and-grow
     * flicker) and whatever size the user chose is preserved. The {@code width}
     * and {@code height} arguments are only used for the very first scene.</p>
     */
    public static void show(Stage stage, ViewTuple<?, ?> tuple, String title,
                            double width, double height, Runnable onEscape) {
        Parent view = tuple.getView();
        Scene scene = sceneOf(stage);
        stage.setTitle(title);
        if (scene == null) {
            // First screen: build the scene, then maximize before the window is
            // visible so it opens already full size.
            scene = new Scene(view, width, height);
            installEscapeHandler(scene);
            stage.getProperties().put(SCENE_PROPERTY, scene);
            stage.setScene(scene);
            if (!stage.isShowing()) {
                openMaximized(stage);
            }
        } else {
            // Same scene, new root: the stage size never changes, so there is no
            // shrink-and-grow flicker when opening a module.
            scene.setRoot(view);
        }
        escapeAction(scene).set(onEscape);
    }

    /** The scene this navigator installed on {@code stage}, or {@code null}. */
    private static Scene sceneOf(Stage stage) {
        Object scene = stage.getProperties().get(SCENE_PROPERTY);
        return scene instanceof Scene current ? current : null;
    }

    /**
     * Opens the window full screen: it is maximized but stays resizable, so the
     * user can restore, move or resize it from then on.
     *
     * <p>Before maximizing, the stage is given the whole usable area of its
     * screen. That is the size the platform remembers as the "restored" one, and
     * the platform goes back to it whenever the window is restored, including
     * when a modal dialog takes the focus: starting from the screen makes that
     * restore invisible, so opening a confirmation dialog never resizes the
     * window.</p>
     */
    public static void openMaximized(Stage stage) {
        stage.setResizable(true);
        fillScreen(stage);
        stage.setMaximized(true);
    }

    /** Gives the stage the usable area of its screen, like a maximized window. */
    private static void fillScreen(Stage stage) {
        Rectangle2D screen = screenOf(stage).getVisualBounds();
        stage.setWidth(screen.getWidth());
        stage.setHeight(screen.getHeight());
    }

    /** The screen the stage is on, or the primary one before it is shown. */
    private static Screen screenOf(Stage stage) {
        double x = stage.getX();
        double y = stage.getY();
        if (Double.isFinite(x) && Double.isFinite(y) && stage.getWidth() > 0
                && stage.getHeight() > 0) {
            for (Screen screen : Screen.getScreensForRectangle(x, y,
                    stage.getWidth(), stage.getHeight())) {
                return screen;
            }
        }
        return Screen.getPrimary();
    }

    /** Runs the ESC action of {@code scene} unless the user is editing a field. */
    private static void installEscapeHandler(Scene scene) {
        scene.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            Runnable onEscape = escapeAction(scene).get();
            if (event.getCode() != KeyCode.ESCAPE || onEscape == null
                    || isEditing(scene.getFocusOwner())) {
                return;
            }
            onEscape.run();
            event.consume();
        });
    }

    /** Mutable holder of the current ESC action, kept on the scene properties. */
    @SuppressWarnings("unchecked")
    private static ObjectProperty<Runnable> escapeAction(Scene scene) {
        Object property = scene.getProperties().get(ESCAPE_PROPERTY);
        if (property instanceof ObjectProperty<?> existing) {
            return (ObjectProperty<Runnable>) existing;
        }
        ObjectProperty<Runnable> created = new SimpleObjectProperty<>();
        scene.getProperties().put(ESCAPE_PROPERTY, created);
        return created;
    }

    /** A text field or a combo keeps the ESC key for itself while it has the focus. */
    private static boolean isEditing(Node focusOwner) {
        return focusOwner instanceof TextInputControl || focusOwner instanceof ComboBoxBase;
    }
}
