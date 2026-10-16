package com.entidaddesconocida.monte_erp.app;

import com.entidaddesconocida.monte_erp.domain.model.User;
import com.entidaddesconocida.monte_erp.view.CategoriesView;
import com.entidaddesconocida.monte_erp.view.CustomersView;
import com.entidaddesconocida.monte_erp.view.LoginView;
import com.entidaddesconocida.monte_erp.view.MainView;
import com.entidaddesconocida.monte_erp.view.ProductsView;
import com.entidaddesconocida.monte_erp.view.SalesOrdersView;
import com.entidaddesconocida.monte_erp.view.SetupView;
import com.entidaddesconocida.monte_erp.view.SuppliersView;
import com.entidaddesconocida.monte_erp.view.UsersView;
import com.entidaddesconocida.monte_erp.viewmodel.CategoriesViewModel;
import com.entidaddesconocida.monte_erp.viewmodel.CustomersViewModel;
import com.entidaddesconocida.monte_erp.viewmodel.LoginViewModel;
import com.entidaddesconocida.monte_erp.viewmodel.ProductsViewModel;
import com.entidaddesconocida.monte_erp.viewmodel.SalesOrdersViewModel;
import com.entidaddesconocida.monte_erp.viewmodel.SetupViewModel;
import com.entidaddesconocida.monte_erp.viewmodel.SuppliersViewModel;
import com.entidaddesconocida.monte_erp.viewmodel.UsersViewModel;

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
        showMain();
    }

    /** Loads the main view for the current session (e.g. when coming back from a module). */
    public void showMain() {
        User user = session.requireCurrentUser();
        ViewTuple<MainView, ?> tuple =
                FluentViewLoader.fxmlView(MainView.class).load();
        String title = "Monte ERP · " + user.getName() + " (" + user.getRoleLabel() + ")";
        SceneNavigator.show(stage, tuple, title,
                SceneNavigator.MAIN_WIDTH, SceneNavigator.MAIN_HEIGHT);
    }

    /**
     * Loads the user management screen (RF-02).
     *
     * @throws IllegalStateException when there is no session or the signed-in
     *                               user is not an administrator; the button is
     *                               hidden for employees, this is a safety net
     */
    public void showUsers() {
        User user = session.requireCurrentUser();
        if (!user.isAdmin()) {
            throw new IllegalStateException("La gestión de usuarios es exclusiva del administrador");
        }
        ViewTuple<UsersView, UsersViewModel> tuple =
                FluentViewLoader.fxmlView(UsersView.class).load();
        SceneNavigator.show(stage, tuple, "Monte ERP · Gestión de usuarios",
                SceneNavigator.USERS_WIDTH, SceneNavigator.USERS_HEIGHT);
    }

    /**
     * Loads the product management screen (RF-03). Every signed-in user may
     * manage products (CU-03 actor is the employee), so there is no role guard.
     */
    public void showProducts() {
        session.requireCurrentUser();
        ViewTuple<ProductsView, ProductsViewModel> tuple =
                FluentViewLoader.fxmlView(ProductsView.class).load();
        SceneNavigator.show(stage, tuple, "Monte ERP · Gestión de productos",
                SceneNavigator.PRODUCTS_WIDTH, SceneNavigator.PRODUCTS_HEIGHT);
    }

    /**
     * Loads the category management screen (RF-04). Every signed-in user may
     * manage categories (CU-04 actor is the employee), so there is no role guard.
     */
    public void showCategories() {
        session.requireCurrentUser();
        ViewTuple<CategoriesView, CategoriesViewModel> tuple =
                FluentViewLoader.fxmlView(CategoriesView.class).load();
        SceneNavigator.show(stage, tuple, "Monte ERP · Gestión de categorías",
                SceneNavigator.CATEGORIES_WIDTH, SceneNavigator.CATEGORIES_HEIGHT);
    }

    /**
     * Loads the supplier management screen (RF-05). Every signed-in user may
     * manage suppliers (CU-05 actor is the employee), so there is no role guard.
     */
    public void showSuppliers() {
        session.requireCurrentUser();
        ViewTuple<SuppliersView, SuppliersViewModel> tuple =
                FluentViewLoader.fxmlView(SuppliersView.class).load();
        SceneNavigator.show(stage, tuple, "Monte ERP · Gestión de proveedores",
                SceneNavigator.SUPPLIERS_WIDTH, SceneNavigator.SUPPLIERS_HEIGHT);
    }

    /**
     * Loads the customer management screen (RF-06). Every signed-in user may
     * manage customers (CU-06 actor is the employee), so there is no role guard.
     */
    public void showCustomers() {
        session.requireCurrentUser();
        ViewTuple<CustomersView, CustomersViewModel> tuple =
                FluentViewLoader.fxmlView(CustomersView.class).load();
        SceneNavigator.show(stage, tuple, "Monte ERP · Gestión de clientes",
                SceneNavigator.CUSTOMERS_WIDTH, SceneNavigator.CUSTOMERS_HEIGHT);
    }

    /**
     * Loads the sales-order screen (RF-07). Every signed-in user may create
     * orders (CU-07 actor is the employee), so there is no role guard.
     */
    public void showSalesOrders() {
        session.requireCurrentUser();
        ViewTuple<SalesOrdersView, SalesOrdersViewModel> tuple =
                FluentViewLoader.fxmlView(SalesOrdersView.class).load();
        SceneNavigator.show(stage, tuple, "Monte ERP · Gestión de pedidos",
                SceneNavigator.SALES_ORDERS_WIDTH, SceneNavigator.SALES_ORDERS_HEIGHT);
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
