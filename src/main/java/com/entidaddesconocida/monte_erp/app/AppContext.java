package com.entidaddesconocida.monte_erp.app;

import com.entidaddesconocida.monte_erp.domain.repository.CategoryRepository;
import com.entidaddesconocida.monte_erp.domain.repository.ProductRepository;
import com.entidaddesconocida.monte_erp.domain.repository.SupplierRepository;
import com.entidaddesconocida.monte_erp.domain.repository.UserRepository;
import com.entidaddesconocida.monte_erp.domain.service.SetupService;
import com.entidaddesconocida.monte_erp.infrastructure.config.DatabaseConfig;
import com.entidaddesconocida.monte_erp.infrastructure.config.DatabaseUrl;
import com.entidaddesconocida.monte_erp.view.CategoriesView;
import com.entidaddesconocida.monte_erp.view.LoginView;
import com.entidaddesconocida.monte_erp.view.MainView;
import com.entidaddesconocida.monte_erp.view.ProductsView;
import com.entidaddesconocida.monte_erp.view.SetupView;
import com.entidaddesconocida.monte_erp.view.UsersView;
import com.entidaddesconocida.monte_erp.viewmodel.CategoriesViewModel;
import com.entidaddesconocida.monte_erp.viewmodel.LoginViewModel;
import com.entidaddesconocida.monte_erp.viewmodel.MainViewModel;
import com.entidaddesconocida.monte_erp.viewmodel.ProductsViewModel;
import com.entidaddesconocida.monte_erp.viewmodel.SetupViewModel;
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
 *   <li>Views and ViewModels are bound with factories ({@link Supplier}) so
 *       each view load gets a fresh instance. Binding an instance directly
 *       would make two loads of the same view share state → subtle bug.</li>
 * </ul>
 */
public final class AppContext {

    private final Map<Class<?>, Supplier<?>> bindings = new HashMap<>();

    public AppContext(SessionState session, AppShell appShell, UserRepository userRepository,
                      CategoryRepository categoryRepository, SupplierRepository supplierRepository,
                      ProductRepository productRepository,
                      DatabaseConfig databaseConfig, SetupService setupService,
                      Supplier<DatabaseUrl> databaseUrl) {
        // Stateless resources: single instance for the whole application.
        bind(UserRepository.class, () -> userRepository);
        bind(CategoryRepository.class, () -> categoryRepository);
        bind(SupplierRepository.class, () -> supplierRepository);
        bind(ProductRepository.class, () -> productRepository);
        bind(DatabaseConfig.class, () -> databaseConfig);
        bind(SetupService.class, () -> setupService);
        bind(DatabaseUrl.class, databaseUrl); // configuration (current JDBC URL)

        // Views (code-behind of the FXML): mvvmFX asks here. ALWAYS fresh instances.
        bind(LoginView.class, () -> new LoginView(appShell));
        bind(MainView.class, () -> new MainView(appShell));
        bind(SetupView.class, () -> new SetupView(appShell));
        bind(UsersView.class, () -> new UsersView(appShell));
        bind(ProductsView.class, () -> new ProductsView(appShell));
        bind(CategoriesView.class, () -> new CategoriesView(appShell));

        // ViewModels: constructor injection, one new instance per view load.
        bind(LoginViewModel.class, () -> new LoginViewModel(userRepository));
        bind(MainViewModel.class,
                () -> new MainViewModel(session.requireCurrentUser(), databaseUrl.get()));
        bind(UsersViewModel.class,
                () -> new UsersViewModel(userRepository, session.requireCurrentUser()));
        bind(ProductsViewModel.class,
                () -> new ProductsViewModel(productRepository, categoryRepository));
        bind(CategoriesViewModel.class,
                () -> new CategoriesViewModel(categoryRepository));
        bind(SetupViewModel.class,
                () -> new SetupViewModel(setupService, appShell.isOnboarding()));
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
