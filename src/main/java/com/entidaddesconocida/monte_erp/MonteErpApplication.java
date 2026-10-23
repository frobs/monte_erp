package com.entidaddesconocida.monte_erp;

import com.entidaddesconocida.monte_erp.app.AppContext;
import com.entidaddesconocida.monte_erp.app.AppShell;
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
import javafx.stage.Stage;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Monte ERP JavaFX application.
 *
 * <p>On startup it decides which screen to show. If the configuration (URL in a
 * properties file next to the application, credentials in the OS keyring) is
 * missing or incomplete, the onboarding screen is shown. Otherwise the pending
 * migrations are applied and the login screen opens (or onboarding again when
 * no administrator user exists yet, e.g. after the database was recreated).</p>
 */
public class MonteErpApplication extends Application {

    private static final Logger LOGGER = Logger.getLogger(MonteErpApplication.class.getName());

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
        AppShell appShell = new AppShell(session);
        AppContext context = new AppContext(session, appShell, userRepository,
                categoryRepository, supplierRepository, productRepository, customerRepository,
                salesOrderRepository, purchaseRepository, stockRepository, databaseConfig,
                setupService, () -> new DatabaseUrl(DatabaseConnection.getUrl()));

        MvvmFX.setCustomDependencyInjector(context::create);
        appShell.setStage(stage);

        try {
            databaseConfig.ensureFileExists();
            ConnectionSettings settings = databaseConfig.load();
            if (!settings.isComplete()) {
                appShell.showSetup(true);
            } else {
                prepareDatabase(setupService, settings, appShell);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Could not read the database configuration", e);
            appShell.showSetup(true);
        }

        stage.show();
    }

    private void prepareDatabase(SetupService setupService, ConnectionSettings settings,
                                 AppShell appShell) throws Exception {
        try {
            DatabaseConnection.configure(settings.url(), settings.user(), settings.password());
            MigrationRunner.migrate();
            if (!setupService.hasAdminUser()) {
                appShell.showSetup(true);
            } else {
                appShell.showLogin();
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Could not prepare the database", e);
            // The configuration exists but is wrong or unreachable: let the user fix it.
            appShell.showSetup(false);
        }
    }

    @Override
    public void stop() {
        DatabaseConnection.close();
    }
}
