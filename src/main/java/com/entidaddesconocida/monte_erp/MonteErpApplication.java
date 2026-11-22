package com.entidaddesconocida.monte_erp;

import com.entidaddesconocida.monte_erp.app.AppContext;
import com.entidaddesconocida.monte_erp.app.AppServices;
import com.entidaddesconocida.monte_erp.app.BackgroundTasks;
import com.entidaddesconocida.monte_erp.app.AppShell;
import com.entidaddesconocida.monte_erp.app.Repositories;
import com.entidaddesconocida.monte_erp.app.SceneNavigator;
import com.entidaddesconocida.monte_erp.app.SessionState;
import com.entidaddesconocida.monte_erp.domain.model.ConnectionSettings;
import com.entidaddesconocida.monte_erp.domain.repository.CategoryRepository;
import com.entidaddesconocida.monte_erp.domain.repository.CustomerRepository;
import com.entidaddesconocida.monte_erp.domain.repository.ProductRepository;
import com.entidaddesconocida.monte_erp.domain.repository.PurchaseRepository;
import com.entidaddesconocida.monte_erp.domain.repository.SalesOrderRepository;
import com.entidaddesconocida.monte_erp.domain.repository.StockRepository;
import com.entidaddesconocida.monte_erp.domain.repository.SupplierRepository;
import com.entidaddesconocida.monte_erp.domain.repository.UserRepository;
import com.entidaddesconocida.monte_erp.domain.service.SetupService;
import com.entidaddesconocida.monte_erp.infrastructure.MySqlCategoryRepository;
import com.entidaddesconocida.monte_erp.infrastructure.MySqlCustomerRepository;
import com.entidaddesconocida.monte_erp.infrastructure.MySqlProductRepository;
import com.entidaddesconocida.monte_erp.infrastructure.MySqlPurchaseRepository;
import com.entidaddesconocida.monte_erp.infrastructure.MySqlSalesOrderRepository;
import com.entidaddesconocida.monte_erp.infrastructure.MySqlStockRepository;
import com.entidaddesconocida.monte_erp.infrastructure.MySqlSupplierRepository;
import com.entidaddesconocida.monte_erp.infrastructure.MySqlUserRepository;
import com.entidaddesconocida.monte_erp.infrastructure.config.DatabaseConfig;
import com.entidaddesconocida.monte_erp.infrastructure.config.DatabaseConnection;
import com.entidaddesconocida.monte_erp.infrastructure.config.DatabaseUrl;
import com.entidaddesconocida.monte_erp.infrastructure.config.DatabaseSetupService;
import com.entidaddesconocida.monte_erp.infrastructure.config.KeyringCredentialStore;
import com.entidaddesconocida.monte_erp.infrastructure.config.MigrationRunner;

import de.saxsys.mvvmfx.MvvmFX;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Monte ERP JavaFX application.
 *
 * <p>Every database operation opens and closes its own connection
 * ({@code DatabaseConnection}), so there is no resource to release on shutdown;
 * the blocking queries run on virtual threads ({@code BackgroundTasks}).</p>
 *
 * <p>On startup it decides which screen to show. If the configuration (URL in a
 * properties file next to the application, credentials in the OS keyring) is
 * missing or incomplete, the onboarding screen is shown. Otherwise the pending
 * migrations are applied and the login screen opens (or onboarding again when
 * no administrator user exists yet, e.g. after the database was recreated).</p>
 *
 * <p>The window is shown <strong>before</strong> any of that happens: opening the
 * connection, applying the migrations and probing the administrator are JDBC
 * calls that can take seconds (or wait for a MySQL timeout), so they run on a
 * virtual thread while the stage paints a progress screen, and only the chosen
 * view is loaded back on the JavaFX thread.</p>
 */
public class MonteErpApplication extends Application {

    private static final Logger LOGGER = Logger.getLogger(MonteErpApplication.class.getName());

    private static final String STARTUP_TITLE = "Monte ERP · Preparando la base de datos";

    /** Screen the session opens with, decided off the JavaFX thread. */
    enum FirstScreen {
        /** Configuration complete and the schema migrated: ask for the credentials. */
        LOGIN,
        /** First run (no configuration, or no administrator yet): create them. */
        ONBOARDING,
        /** Configuration present but unusable: let the user fix the connection. */
        SETUP
    }

    @Override
    public void start(Stage stage) {
        DatabaseConfig databaseConfig = new DatabaseConfig(new KeyringCredentialStore());
        UserRepository userRepository = new MySqlUserRepository();
        CategoryRepository categoryRepository = new MySqlCategoryRepository();
        SupplierRepository supplierRepository = new MySqlSupplierRepository();
        ProductRepository productRepository = new MySqlProductRepository();
        CustomerRepository customerRepository = new MySqlCustomerRepository();
        SalesOrderRepository salesOrderRepository = new MySqlSalesOrderRepository();
        PurchaseRepository purchaseRepository = new MySqlPurchaseRepository();
        StockRepository stockRepository = new MySqlStockRepository();
        SetupService setupService = new DatabaseSetupService(databaseConfig, userRepository);

        SessionState session = new SessionState();
        AppShell appShell = new AppShell(session, userRepository);
        Repositories repositories = new Repositories(userRepository, categoryRepository,
                supplierRepository, productRepository, customerRepository, salesOrderRepository,
                purchaseRepository, stockRepository);
        AppServices services = new AppServices(databaseConfig, setupService,
                () -> new DatabaseUrl(DatabaseConnection.getUrl()),
                BackgroundTasks.virtualThreads());
        AppContext context = new AppContext(session, appShell, repositories, services);

        MvvmFX.setCustomDependencyInjector(context::create);
        appShell.setStage(stage);

        // The window is painted before the database is touched, so the user sees
        // the application starting instead of nothing while MySQL answers.
        SceneNavigator.show(stage, preparingRoot(), STARTUP_TITLE,
                SceneNavigator.LOGIN_WIDTH, SceneNavigator.LOGIN_HEIGHT);
        stage.show();

        Thread.startVirtualThread(() -> {
            FirstScreen first = firstScreen(databaseConfig, setupService);
            // Loading a view touches the JavaFX scene graph: back to the UI thread.
            Platform.runLater(() -> show(appShell, first));
        });
    }

    /**
     * Decides which screen opens the session, applying the pending migrations and
     * probing the administrator on the way.
     *
     * <p>Blocking on purpose: it is called from a virtual thread, never from the
     * JavaFX thread. It always returns a screen to show, so a failure leaves the
     * user in the setup screen with its message instead of a dead window.</p>
     */
    static FirstScreen firstScreen(DatabaseConfig databaseConfig, SetupService setupService) {
        ConnectionSettings settings;
        try {
            databaseConfig.ensureFileExists();
            settings = databaseConfig.load();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Could not read the database configuration", e);
            return FirstScreen.ONBOARDING;
        }
        if (!settings.isComplete()) {
            return FirstScreen.ONBOARDING;
        }
        try {
            DatabaseConnection.configure(settings.url(), settings.user(), settings.password());
            MigrationRunner.migrate();
            return setupService.hasAdminUser() ? FirstScreen.LOGIN : FirstScreen.ONBOARDING;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Could not prepare the database", e);
            // The configuration exists but is wrong or unreachable: let the user fix it.
            return FirstScreen.SETUP;
        }
    }

    /** Loads the view of the chosen screen; runs on the JavaFX thread. */
    private static void show(AppShell appShell, FirstScreen first) {
        switch (first) {
            case LOGIN -> appShell.showLogin();
            case ONBOARDING -> appShell.showSetup(true);
            case SETUP -> appShell.showSetup(false);
        }
    }

    /**
     * Root of the scene shown while {@link #firstScreen} runs: a spinner and a
     * message, with no ViewModel and no FXML, so it can be painted before the
     * dependency injector has been used for the first time.
     */
    private static Parent preparingRoot() {
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(48, 48);
        Label message = new Label("Preparando la base de datos…");
        VBox box = new VBox(16, spinner, message);
        box.setAlignment(Pos.CENTER);
        StackPane root = new StackPane(box);
        root.setId("startup-progress");
        return root;
    }
}
