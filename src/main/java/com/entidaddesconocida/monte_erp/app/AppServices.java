package com.entidaddesconocida.monte_erp.app;

import com.entidaddesconocida.monte_erp.domain.service.SetupService;
import com.entidaddesconocida.monte_erp.infrastructure.config.DatabaseConfig;
import com.entidaddesconocida.monte_erp.infrastructure.config.DatabaseUrl;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * The infrastructure services the composition root hands to {@link AppContext}.
 *
 * <p>They are not repositories: one reads/writes the configuration, one runs the
 * onboarding against the database, one exposes the <em>current</em> JDBC URL
 * (read lazily, because it changes when the connection is reconfigured) and one
 * is the executor the ViewModels use for the blocking work.</p>
 *
 * @param databaseConfig reads and writes the connection settings
 * @param setupService   creates the schema and the first administrator
 * @param databaseUrl    lazily read URL of the active connection
 * @param backgroundTasks executor where the ViewModels run their JDBC queries
 */
public record AppServices(DatabaseConfig databaseConfig,
                          SetupService setupService,
                          Supplier<DatabaseUrl> databaseUrl,
                          BackgroundTasks backgroundTasks) {

    public AppServices {
        Objects.requireNonNull(databaseConfig, "databaseConfig");
        Objects.requireNonNull(setupService, "setupService");
        Objects.requireNonNull(databaseUrl, "databaseUrl");
        Objects.requireNonNull(backgroundTasks, "backgroundTasks");
    }
}
