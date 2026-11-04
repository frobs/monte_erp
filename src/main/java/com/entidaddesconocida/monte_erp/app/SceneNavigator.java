package com.entidaddesconocida.monte_erp.app;

import de.saxsys.mvvmfx.ViewTuple;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Dimension2D;
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
 * <p>Since a maximized window is restored (system button) to the bounds it had
 * before being maximized, the stage is given a size that makes sense on its
 * screen before maximizing: restoring the window never brings back the small
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

    /**
     * Share of the screen used when the window is restored with the system
     * button, so it is large enough to read the tables and forms.
     */
    private static final double RESTORE_SCREEN_RATIO = 0.85;

    /** Key of the scene owned by this navigator inside {@link Stage#getProperties()}. */
    private static final String SCENE_PROPERTY = "monte-erp.scene";
    /** Key of the action currently bound to ESC inside {@link Scene#getProperties()}. */
    private static final String ESCAPE_PROPERTY = "monte-erp.on-escape";
    /** Key of the flag marking the restored size as already fixed once. */
    private static final String RESTORE_FIXED_PROPERTY = "monte-erp.restore-fixed";

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
                // Full screen by default, but never a non-resizable window: if the
                // user restores or resizes it, the choice is respected from then on.
                stage.setResizable(true);
                // The platform restores a maximized window to the bounds it had
                // before being maximized, so give it a sensible size first: this
                // is what the user sees after pressing the restore button.
                applySensibleSize(stage);
                keepSensibleSizeOnRestore(stage);
                stage.setMaximized(true);
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
     * Sizes the stage as if it had just been restored from a maximized state:
     * a large part of its screen, but never smaller than the main menu.
     */
    private static void applySensibleSize(Stage stage) {
        Dimension2D size = sensibleSize(screenOf(stage));
        stage.setWidth(size.getWidth());
        stage.setHeight(size.getHeight());
    }

    /**
     * Safety net for platforms that ignore the size set before maximizing: the
     * first time the window is restored, if it came back too small to be usable,
     * it is enlarged and centred. Later restores keep the size the user chose.
     */
    private static void keepSensibleSizeOnRestore(Stage stage) {
        stage.maximizedProperty().addListener((observable, wasMaximized, isMaximized) -> {
            if (!wasMaximized || isMaximized
                    || stage.getProperties().containsKey(RESTORE_FIXED_PROPERTY)) {
                return;
            }
            stage.getProperties().put(RESTORE_FIXED_PROPERTY, Boolean.TRUE);
            // Let the platform finish restoring before measuring the window.
            Platform.runLater(() -> enlargeAndCenter(stage));
        });
    }

    /** Grows a restored window that ended up smaller than a sensible size. */
    private static void enlargeAndCenter(Stage stage) {
        Screen screen = screenOf(stage);
        Dimension2D size = sensibleSize(screen);
        if (stage.getWidth() >= size.getWidth() && stage.getHeight() >= size.getHeight()) {
            return;
        }
        stage.setWidth(Math.max(stage.getWidth(), size.getWidth()));
        stage.setHeight(Math.max(stage.getHeight(), size.getHeight()));
        stage.setX(screen.getVisualBounds().getMinX() + (screen.getVisualBounds().getWidth()
                - stage.getWidth()) / 2);
        stage.setY(screen.getVisualBounds().getMinY() + (screen.getVisualBounds().getHeight()
                - stage.getHeight()) / 2);
    }

    /**
     * Size that makes sense for a restored window: most of the screen, but never
     * smaller than the main menu and never larger than the available area.
     */
    private static Dimension2D sensibleSize(Screen screen) {
        double availableWidth = screen.getVisualBounds().getWidth();
        double availableHeight = screen.getVisualBounds().getHeight();
        double width = Math.min(availableWidth,
                Math.max(MAIN_WIDTH, Math.round(availableWidth * RESTORE_SCREEN_RATIO)));
        double height = Math.min(availableHeight,
                Math.max(MAIN_HEIGHT, Math.round(availableHeight * RESTORE_SCREEN_RATIO)));
        return new Dimension2D(width, height);
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
