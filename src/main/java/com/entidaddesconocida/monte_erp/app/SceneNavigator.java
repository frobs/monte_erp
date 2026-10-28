package com.entidaddesconocida.monte_erp.app;

import de.saxsys.mvvmfx.ViewTuple;

import javafx.scene.Scene;
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
        stage.setTitle(title);
        stage.setScene(new Scene(tuple.getView(), width, height));
        if (!stage.isShowing()) {
            // Full screen by default, but never a non-resizable window: if the user
            // restores or resizes it, the choice is respected from then on.
            stage.setResizable(true);
            stage.setMaximized(true);
        }
    }
}
