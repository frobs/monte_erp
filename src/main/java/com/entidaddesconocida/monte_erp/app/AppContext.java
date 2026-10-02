package com.entidaddesconocida.monte_erp.app;

import com.entidaddesconocida.monte_erp.domain.repository.UserRepository;
import com.entidaddesconocida.monte_erp.infrastructure.MySqlUserRepository;
import com.entidaddesconocida.monte_erp.infrastructure.config.DatabaseConnection;
import com.entidaddesconocida.monte_erp.view.LoginView;
import com.entidaddesconocida.monte_erp.view.MainView;
import com.entidaddesconocida.monte_erp.viewmodel.LoginViewModel;
import com.entidaddesconocida.monte_erp.viewmodel.MainViewModel;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Composition root and lightweight dependency-injection container.
 *
 * <p>This is the <strong>only</strong> place in the application that creates
 * the dependency chain with {@code new}. Every other class receives its
 * collaborators through constructor injection.</p>
 *
 * <p>Convention:</p>
 * <ul>
 *   <li>Stateless resources (repository, configuration strings) are bound as
 *       singleton instances.</li>
 *   <li>Views and ViewModels are bound with factories ({@link Supplier}) so
 *       each view load gets a fresh instance. Binding an instance directly
 *       would make two loads of the same view share state → subtle bug.</li>
 * </ul>
 */
public final class AppContext {

    private final Map<Class<?>, Supplier<?>> bindings = new HashMap<>();

    public AppContext(SessionState session, AppShell appShell, UserRepository userRepository,
                      String databaseUrl) {
        // Stateless resources: single instance for the whole application.
        bind(UserRepository.class, () -> userRepository);
        bind(String.class, () -> databaseUrl); // configuration

        // Views (code-behind of the FXML): mvvmFX asks here. ALWAYS fresh instances.
        bind(LoginView.class, () -> new LoginView(appShell));
        bind(MainView.class, () -> new MainView(appShell));

        // ViewModels: constructor injection, one new instance per view load.
        bind(LoginViewModel.class, () -> new LoginViewModel(userRepository));
        bind(MainViewModel.class, () -> new MainViewModel(session.requireCurrentUser(), databaseUrl));
    }

    /** Wiring of production: real JDBC implementation and project configuration. */
    public static AppContext production(SessionState session, AppShell appShell) {
        return new AppContext(session, appShell, new MySqlUserRepository(), DatabaseConnection.getUrl());
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
