package com.entidaddesconocida.monte_erp.app;

import com.entidaddesconocida.monte_erp.domain.model.User;
import com.entidaddesconocida.monte_erp.view.LoginView;
import com.entidaddesconocida.monte_erp.view.MainView;
import com.entidaddesconocida.monte_erp.viewmodel.LoginViewModel;

import de.saxsys.mvvmfx.FluentViewLoader;
import de.saxsys.mvvmfx.ViewTuple;

import javafx.stage.Stage;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Owns the {@link Stage}, handles navigation between views and wires global
 * notifications.
 *
 * <p>It is created <strong>before</strong> {@link AppContext} so the composition
 * root can inject it into the Views that need to navigate or close the
 * application.</p>
 */
public class AppShell {

    private static final Logger LOGGER = Logger.getLogger(AppShell.class.getName());

    private final SessionState session;
    private Stage stage;

    public AppShell(SessionState session) {
        this.session = session;
    }

    /** Stores the stage and shows the login view. */
    public void start(Stage stage) {
        this.stage = stage;
        showLogin();
        stage.show();
    }

    /** Loads the login view and subscribes to the success notification. */
    public void showLogin() {
        session.signOut();
        ViewTuple<LoginView, LoginViewModel> tuple =
                FluentViewLoader.fxmlView(LoginView.class).load();
        SceneNavigator.show(stage, tuple, "Monte ERP · Iniciar sesión",
                SceneNavigator.LOGIN_WIDTH, SceneNavigator.LOGIN_HEIGHT);
    }

    /** Signs the user in and loads the main view. */
    public void showMain(User user) {
        session.signIn(user);
        ViewTuple<MainView, ?> tuple =
                FluentViewLoader.fxmlView(MainView.class).load();
        String title = "Monte ERP · " + user.getName() + " (" + user.getRoleLabel() + ")";
        SceneNavigator.show(stage, tuple, title,
                SceneNavigator.MAIN_WIDTH, SceneNavigator.MAIN_HEIGHT);
    }

    /** Closes the application window. */
    public void exit() {
        if (stage != null) {
            stage.close();
        }
    }
}
