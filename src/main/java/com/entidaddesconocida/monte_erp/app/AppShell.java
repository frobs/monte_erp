package com.entidaddesconocida.monte_erp.app;

import com.entidaddesconocida.monte_erp.domain.model.User;
import com.entidaddesconocida.monte_erp.view.LoginView;
import com.entidaddesconocida.monte_erp.view.MainView;
import com.entidaddesconocida.monte_erp.view.SetupView;
import com.entidaddesconocida.monte_erp.viewmodel.LoginViewModel;
import com.entidaddesconocida.monte_erp.viewmodel.SetupViewModel;

import de.saxsys.mvvmfx.FluentViewLoader;
import de.saxsys.mvvmfx.ViewTuple;

import javafx.stage.Stage;

/**
 * Owns the {@link Stage}, handles navigation between views and wires global
 * notifications.
 *
 * <p>It is created <strong>before</strong> {@link AppContext} so the composition
 * root can inject it into the Views that need to navigate or close the
 * application.</p>
 */
public class AppShell {

    private final SessionState session;
    private Stage stage;
    private boolean onboardingMode;

    public AppShell(SessionState session) {
        this.session = session;
    }

    /** Stores the stage; the first view is shown by the application entry point. */
    public void setStage(Stage stage) {
        this.stage = stage;
    }

    /** {@code true} while the setup screen is being shown in onboarding mode. */
    public boolean isOnboarding() {
        return onboardingMode;
    }

    /** Loads the login view. */
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

    /**
     * Loads the database configuration screen.
     *
     * @param onboarding {@code true} for the first-run setup (administrator user
     *                   fields are shown), {@code false} to only change the
     *                   database connection
     */
    public void showSetup(boolean onboarding) {
        this.onboardingMode = onboarding;
        ViewTuple<SetupView, SetupViewModel> tuple =
                FluentViewLoader.fxmlView(SetupView.class).load();
        String title = onboarding
                ? "Monte ERP · Configuración inicial"
                : "Monte ERP · Configuración de la base de datos";
        SceneNavigator.show(stage, tuple, title,
                SceneNavigator.SETUP_WIDTH, SceneNavigator.SETUP_HEIGHT);
    }

    /** Cancels the setup screen: exits on first run, otherwise back to login. */
    public void cancelSetup() {
        if (onboardingMode) {
            exit();
        } else {
            showLogin();
        }
    }

    /** Closes the application window. */
    public void exit() {
        if (stage != null) {
            stage.close();
        }
    }
}
