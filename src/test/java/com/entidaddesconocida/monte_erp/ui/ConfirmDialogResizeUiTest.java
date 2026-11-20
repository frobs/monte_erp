package com.entidaddesconocida.monte_erp.ui;

import com.entidaddesconocida.monte_erp.app.SceneNavigator;
import com.entidaddesconocida.monte_erp.ui.support.AbstractUiTest;
import com.entidaddesconocida.monte_erp.ui.support.FxRunner;

import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RNF-05 / CU-08: the destructive confirmation of a sales order must be a modal
 * dialog on top of the window, without touching the window itself.
 *
 * <p>The application opens maximized ({@code SceneNavigator} maximizes the stage
 * the first time a scene is shown) and the size the user sees must survive the
 * dialog: the window cannot shrink when the confirmation is displayed. That is
 * the regression this test pins down: the platform restores a maximized window
 * to the bounds it had just before being maximized as soon as a modal dialog
 * takes the focus.</p>
 *
 * <p>{@link com.entidaddesconocida.monte_erp.ui.support.UiTestSupport} deliberately
 * un-maximizes the window to a fixed 1440x900 so the robot always finds the
 * controls in the same place, which would hide the problem: a maximized window
 * behaves differently from a plain sized one. The test therefore puts the window
 * back in the state the application really opens it, reusing
 * {@link SceneNavigator#openMaximized(Stage)} so the dialog is exercised against
 * the production window policy and not against a copy of it.</p>
 */
@DisplayName("Diálogo de confirmación de pedidos")
class ConfirmDialogResizeUiTest extends AbstractUiTest {

    private static final int ORDER_ID = 30;

    /** Window state: the screen real estate the user sees plus the scene size. */
    private record WindowState(double x, double y, double width, double height,
                               double sceneWidth, double sceneHeight, boolean maximized) {
    }

    @Test
    @DisplayName("Abrir el diálogo de cancelar un pedido no redimensiona la ventana principal")
    void cancelDialogKeepsTheWindowSize() {
        loginAsAdmin();
        ui.clickButton("Pedidos");
        ui.assertTitle("Monte ERP · Gestión de pedidos");

        FxRunner.run(() -> SceneNavigator.openMaximized(stage));
        awaitMaximized();
        WindowState before = windowState();

        try {
            ui.clickTableAction("ordersTable", "#" + ORDER_ID, "Cancelar");

            assertTrue(ui.isDialogShowing(),
                    "Debe abrirse el diálogo de confirmación al cancelar el pedido #" + ORDER_ID);
            assertTrue(ui.dialogText().contains("Cancelar el pedido #" + ORDER_ID),
                    "El diálogo debe ser la confirmación de cancelar el pedido: " + ui.dialogText());

            // While the confirmation is on screen the window must keep exactly the
            // size it had, and so must the view rendered inside it.
            assertEquals(before, windowState(),
                    "El diálogo de confirmación ha redimensionado la ventana principal");
        } finally {
            // Fail fast, but never leave the modal dialog open: showAndWait() blocks
            // the FX thread until it is dismissed, which would hang the suite.
            if (ui.isDialogShowing()) {
                ui.dismissDialog();
            }
        }

        assertEquals(before, windowState(),
                "La ventana principal no debe quedar redimensionada tras cerrar el diálogo");
    }

    /**
     * Waits for the platform to apply the maximize and checks the window really
     * covers its screen, so a platform that ignored the request cannot make the
     * size assertion pass by comparing two equally un-maximized states.
     */
    private void awaitMaximized() {
        FxRunner.waitForFxEvents();

        WindowState previous = null;
        for (int attempt = 0; attempt < 50; attempt++) {
            WindowState current = windowState();
            if (current.equals(previous)) {
                assertTrue(isFullScreenOfItsScreen(current),
                        "No se pudo maximizar la ventana (estado " + current + "): "
                                + "el test necesita la ventana maximizada, como la abre la aplicación");
                return;
            }
            previous = current;
            pause();
        }
        throw new AssertionError("La ventana no se estabilizó después de maximizarla: " + previous);
    }

    private WindowState windowState() {
        return FxRunner.get(() -> {
            double sceneWidth = stage.getScene() == null ? 0 : stage.getScene().getWidth();
            double sceneHeight = stage.getScene() == null ? 0 : stage.getScene().getHeight();
            return new WindowState(stage.getX(), stage.getY(), stage.getWidth(),
                    stage.getHeight(), sceneWidth, sceneHeight, stage.isMaximized());
        });
    }

    /** Whether the window covers the screen it occupies, as when maximized. */
    private boolean isFullScreenOfItsScreen(WindowState state) {
        List<Screen> screens = Screen.getScreensForRectangle(
                state.x(), state.y(), state.width(), state.height());
        Rectangle2D screen = screens.isEmpty() ? Screen.getPrimary().getVisualBounds()
                : screens.get(0).getVisualBounds();
        return state.maximized()
                && state.width() >= screen.getWidth() - 2
                && state.height() >= screen.getHeight() - 2;
    }

    private static void pause() {
        try {
            Thread.sleep(20);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrumpido mientras se maximizaba la ventana", e);
        }
    }
}
