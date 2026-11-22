package com.entidaddesconocida.monte_erp.app;

import com.entidaddesconocida.monte_erp.domain.repository.CategoryRepository;
import com.entidaddesconocida.monte_erp.domain.repository.CustomerRepository;
import com.entidaddesconocida.monte_erp.domain.repository.ProductRepository;
import com.entidaddesconocida.monte_erp.domain.repository.PurchaseRepository;
import com.entidaddesconocida.monte_erp.domain.repository.SalesOrderRepository;
import com.entidaddesconocida.monte_erp.domain.repository.StockRepository;
import com.entidaddesconocida.monte_erp.domain.repository.SupplierRepository;
import com.entidaddesconocida.monte_erp.domain.repository.UserRepository;
import com.entidaddesconocida.monte_erp.domain.service.SetupService;
import com.entidaddesconocida.monte_erp.infrastructure.config.DatabaseConfig;
import com.entidaddesconocida.monte_erp.infrastructure.config.DatabaseUrl;
import com.entidaddesconocida.monte_erp.view.CategoriesView;
import com.entidaddesconocida.monte_erp.view.CustomersView;
import com.entidaddesconocida.monte_erp.view.LoginView;
import com.entidaddesconocida.monte_erp.view.MainView;
import com.entidaddesconocida.monte_erp.view.ProductsView;
import com.entidaddesconocida.monte_erp.view.PurchasesView;
import com.entidaddesconocida.monte_erp.view.ReportsView;
import com.entidaddesconocida.monte_erp.view.SalesOrdersView;
import com.entidaddesconocida.monte_erp.view.SetupView;
import com.entidaddesconocida.monte_erp.view.StockView;
import com.entidaddesconocida.monte_erp.view.SuppliersView;
import com.entidaddesconocida.monte_erp.view.UsersView;
import com.entidaddesconocida.monte_erp.viewmodel.CategoriesViewModel;
import com.entidaddesconocida.monte_erp.viewmodel.CustomersViewModel;
import com.entidaddesconocida.monte_erp.viewmodel.LoginViewModel;
import com.entidaddesconocida.monte_erp.viewmodel.MainViewModel;
import com.entidaddesconocida.monte_erp.viewmodel.ProductsViewModel;
import com.entidaddesconocida.monte_erp.viewmodel.PurchasesViewModel;
import com.entidaddesconocida.monte_erp.viewmodel.ReportsViewModel;
import com.entidaddesconocida.monte_erp.viewmodel.SalesOrdersViewModel;
import com.entidaddesconocida.monte_erp.viewmodel.SetupViewModel;
import com.entidaddesconocida.monte_erp.viewmodel.StockViewModel;
import com.entidaddesconocida.monte_erp.viewmodel.SuppliersViewModel;
import com.entidaddesconocida.monte_erp.viewmodel.UsersViewModel;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Composition root and lightweight dependency-injection container.
 *
 * <p>The dependency chain is created in {@code MonteErpApplication} (the
 * composition root) and passed here through constructor injection; this class
 * only wires the mvvmFX bindings. Every other class receives its collaborators
 * through constructor injection.</p>
 *
 * <p>Convention:</p>
 * <ul>
 *   <li>Stateless resources (repositories, configuration) are bound as singleton
 *       instances.</li>
 *   <li>{@link BackgroundTasks} is a stateless executor owned by the composition
 *       root, injected into every ViewModel that runs a query: the blocking JDBC
 *       work never happens on the JavaFX thread.</li>
 *   <li>Views and ViewModels are bound with factories ({@link Supplier}) so
 *       each view load gets a fresh instance. Binding an instance directly
 *       would make two loads of the same view share state → subtle bug.</li>
 * </ul>
 */
public final class AppContext {

    private final Map<Class<?>, Supplier<?>> bindings = new HashMap<>();

    /**
     * Creates the container around the session, the shell and the two dependency
     * groups.
     *
     * <p>Receives {@link Repositories} and {@link AppServices} instead of a dozen
     * loose parameters: every dependency keeps its name at the call site and two
     * collaborators of the same type can no longer be swapped by accident.</p>
     */
    public AppContext(SessionState session, AppShell appShell, Repositories repositories,
                      AppServices services) {
        UserRepository userRepository = repositories.users();
        Supplier<DatabaseUrl> databaseUrl = services.databaseUrl();
        BackgroundTasks backgroundTasks = services.backgroundTasks();
        SessionValidator sessionValidator = new SessionValidator(session, userRepository);
        // Stateless resources: single instance for the whole application.
        bind(UserRepository.class, () -> userRepository);
        bind(CategoryRepository.class, () -> repositories.categories());
        bind(SupplierRepository.class, () -> repositories.suppliers());
        bind(ProductRepository.class, () -> repositories.products());
        bind(CustomerRepository.class, () -> repositories.customers());
        bind(SalesOrderRepository.class, () -> repositories.salesOrders());
        bind(PurchaseRepository.class, () -> repositories.purchases());
        bind(StockRepository.class, () -> repositories.stock());
        bind(DatabaseConfig.class, () -> services.databaseConfig());
        bind(SetupService.class, () -> services.setupService());
        bind(DatabaseUrl.class, databaseUrl); // configuration (current JDBC URL)

        // Views (code-behind of the FXML): mvvmFX asks here. ALWAYS fresh instances.
        bind(LoginView.class, () -> new LoginView(appShell));
        bind(MainView.class, () -> new MainView(appShell));
        bind(SetupView.class, () -> new SetupView(appShell));
        bind(UsersView.class, () -> new UsersView(appShell));
        bind(ProductsView.class, () -> new ProductsView(appShell));
        bind(CategoriesView.class, () -> new CategoriesView(appShell));
        bind(SuppliersView.class, () -> new SuppliersView(appShell));
        bind(CustomersView.class, () -> new CustomersView(appShell));
        bind(SalesOrdersView.class, () -> new SalesOrdersView(appShell));
        bind(PurchasesView.class, () -> new PurchasesView(appShell));
        bind(StockView.class, () -> new StockView(appShell));
        bind(ReportsView.class, () -> new ReportsView(appShell));

        // ViewModels: constructor injection, one new instance per view load.
        bind(LoginViewModel.class, () -> new LoginViewModel(userRepository, backgroundTasks));
        bind(MainViewModel.class,
                () -> new MainViewModel(session.requireCurrentUser(), databaseUrl.get()));
        bind(UsersViewModel.class,
                () -> new UsersViewModel(userRepository, session.requireCurrentUser(),
                        sessionValidator, backgroundTasks));
        bind(ProductsViewModel.class,
                () -> new ProductsViewModel(repositories.products(), repositories.categories(),
                        repositories.suppliers(), sessionValidator, backgroundTasks));
        bind(CategoriesViewModel.class,
                () -> new CategoriesViewModel(repositories.categories(), sessionValidator,
                        backgroundTasks));
        bind(SuppliersViewModel.class,
                () -> new SuppliersViewModel(repositories.suppliers(), sessionValidator,
                        backgroundTasks));
        bind(CustomersViewModel.class,
                () -> new CustomersViewModel(repositories.customers(), sessionValidator,
                        backgroundTasks));
        bind(SalesOrdersViewModel.class,
                () -> new SalesOrdersViewModel(repositories.salesOrders(), repositories.customers(),
                        repositories.products(), repositories.stock(), session.requireCurrentUser(),
                        sessionValidator, backgroundTasks));
        bind(PurchasesViewModel.class,
                () -> new PurchasesViewModel(repositories.purchases(), repositories.suppliers(),
                        repositories.products(), session.requireCurrentUser(), sessionValidator,
                        backgroundTasks));
        bind(StockViewModel.class,
                () -> new StockViewModel(repositories.stock(), sessionValidator, backgroundTasks));
        bind(ReportsViewModel.class,
                () -> new ReportsViewModel(repositories.salesOrders(), repositories.stock(),
                        sessionValidator, backgroundTasks));
        bind(SetupViewModel.class,
                () -> new SetupViewModel(services.setupService(), appShell.isOnboarding(),
                        backgroundTasks));
    }

    /** Callback registered in mvvmFX via {@link de.saxsys.mvvmfx.MvvmFX#setCustomDependencyInjector}. */
    public Object create(Class<?> type) {
        Supplier<?> binding = bindings.get(type);
        if (binding == null) {
            throw new IllegalArgumentException(
                    "No hay binding para " + type.getName() + "; añádelo en AppContext");
        }
        return binding.get();
    }

    private <T> void bind(Class<T> type, Supplier<? extends T> supplier) {
        bindings.put(type, supplier);
    }
}
