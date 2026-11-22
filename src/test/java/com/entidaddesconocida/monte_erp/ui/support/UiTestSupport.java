package com.entidaddesconocida.monte_erp.ui.support;

import com.entidaddesconocida.monte_erp.app.AppContext;
import com.entidaddesconocida.monte_erp.app.AppServices;
import com.entidaddesconocida.monte_erp.app.BackgroundTasks;
import com.entidaddesconocida.monte_erp.app.AppShell;
import com.entidaddesconocida.monte_erp.app.Repositories;
import com.entidaddesconocida.monte_erp.app.SessionState;
import com.entidaddesconocida.monte_erp.domain.model.User;
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
import com.entidaddesconocida.monte_erp.infrastructure.config.DatabaseSetupService;
import com.entidaddesconocida.monte_erp.infrastructure.config.DriverManagerConnectionFactory;
import com.entidaddesconocida.monte_erp.infrastructure.config.DatabaseUrl;
import com.entidaddesconocida.monte_erp.infrastructure.config.FakeCredentialStore;
import com.entidaddesconocida.monte_erp.view.ReportsView;

import de.saxsys.mvvmfx.MvvmFX;

import javafx.stage.Stage;

import java.nio.file.Path;

/**
 * Wires the real application (same composition as {@code MonteErpApplication})
 * on top of the seeded MySQL container, for the TestFX tests.
 *
 * <p>Only {@link ReportsView} is replaced: it receives a
 * {@link FakeFileSaveDialog} so the export button can be exercised without the
 * native "save as" dialog.</p>
 */
public final class UiTestSupport {

    private final Stage stage;
    private final SessionState session = new SessionState();
    private final AppShell appShell;
    private final AppContext context;
    private final FakeFileSaveDialog fileSaveDialog = new FakeFileSaveDialog();

    private UiTestSupport(Stage stage, DatabaseConfig config,
                          DriverManagerConnectionFactory connections) {
        this.stage = stage;
        UserRepository userRepository = new MySqlUserRepository(connections);
        this.appShell = new AppShell(session, userRepository);
        this.appShell.setStage(stage);
        Repositories repositories = new Repositories(userRepository,
                new MySqlCategoryRepository(connections), new MySqlSupplierRepository(connections),
                new MySqlProductRepository(connections), new MySqlCustomerRepository(connections),
                new MySqlSalesOrderRepository(connections), new MySqlPurchaseRepository(connections),
                new MySqlStockRepository(connections));
        SetupService setupService = new DatabaseSetupService(config, userRepository, connections);
        AppServices services = new AppServices(config, setupService,
                () -> new DatabaseUrl(connections.url()),
                // Synchronous on purpose: TestFX drives the real window and asserts
                // right after each click, so a background result would race the
                // assertion. The virtual-thread runner is covered by unit tests.
                BackgroundTasks.synchronous());
        this.context = new AppContext(session, appShell, repositories, services);
        MvvmFX.setCustomDependencyInjector(this::create);
    }

    /** Harness for the seeded demo database (login, modules, reports...). */
    public static UiTestSupport forSeedDatabase(Stage stage) {
        TestDatabase.provision();
        TestDatabase.configureConnection();
        DriverManagerConnectionFactory connections = new DriverManagerConnectionFactory(
                TestDatabase.jdbcUrl(), TestDatabase.username(), TestDatabase.password());
        Path properties = TempFiles.propertiesFile("application.properties", TestDatabase.jdbcUrl());
        FakeCredentialStore credentials = new FakeCredentialStore()
                .with("db.user", TestDatabase.username())
                .with("db.password", TestDatabase.password());
        DatabaseConfig config = new DatabaseConfig(credentials, properties);
        return new UiTestSupport(stage, config, connections);
    }

    /**
     * Harness for the onboarding flow: the setup screen writes its own
     * configuration into {@code propertiesFile} and {@code credentials}.
     */
    public static UiTestSupport forOnboarding(Stage stage, Path propertiesFile,
                                              FakeCredentialStore credentials) {
        DatabaseConfig config = new DatabaseConfig(credentials, propertiesFile);
        // Unconfigured on purpose: saving the setup screen points it at the chosen database.
        return new UiTestSupport(stage, config, new DriverManagerConnectionFactory());
    }

    /** Shows the login screen and makes the window usable by the robot. */
    public void startAtLogin() {
        FxRunner.run(() -> {
            appShell.showLogin();
            showStage();
        });
    }

    /** Shows the setup screen (onboarding or "change connection"). */
    public void startAtSetup(boolean onboarding) {
        FxRunner.run(() -> {
            appShell.showSetup(onboarding);
            showStage();
        });
    }

    /** Signs the user in and shows the main menu. */
    public void startAtMain(User user) {
        FxRunner.run(() -> {
            appShell.showMain(user);
            showStage();
        });
    }

    public void startAtMain() {
        FxRunner.run(() -> {
            appShell.showMain();
            showStage();
        });
    }

    public Stage stage() {
        return stage;
    }

    public AppShell appShell() {
        return appShell;
    }

    public SessionState session() {
        return session;
    }

    public FakeFileSaveDialog fileSaveDialog() {
        return fileSaveDialog;
    }

    /**
     * The window is kept large enough for every module (the widest is 1280 px)
     * while Xvfb provides a bigger virtual screen.
     */
    private void showStage() {
        if (!stage.isShowing()) {
            stage.setMaximized(false);
            stage.setWidth(1440);
            stage.setHeight(900);
            stage.centerOnScreen();
            stage.show();
        }
        stage.toFront();
        stage.requestFocus();
        FxRunner.run(() -> {
            for (int pass = 0; pass < 3 && stage.getScene().getRoot().isNeedsLayout(); pass++) {
                stage.getScene().getRoot().applyCss();
                stage.getScene().getRoot().requestLayout();
                stage.getScene().getRoot().layout();
            }
        });
        FxRunner.waitForFxEvents();
    }

    private Object create(Class<?> type) {
        if (type == ReportsView.class) {
            return new ReportsView(appShell, fileSaveDialog);
        }
        return context.create(type);
    }
}
