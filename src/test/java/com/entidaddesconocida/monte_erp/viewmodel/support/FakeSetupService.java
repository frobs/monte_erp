package com.entidaddesconocida.monte_erp.viewmodel.support;

import com.entidaddesconocida.monte_erp.domain.model.ConnectionSettings;
import com.entidaddesconocida.monte_erp.domain.service.LoadedSetup;
import com.entidaddesconocida.monte_erp.domain.service.SetupException;
import com.entidaddesconocida.monte_erp.domain.service.SetupService;

/**
 * In-memory double of {@link SetupService} for unit tests.
 *
 * <p>Call {@link #throwOnSave()}, {@link #throwOnProvision()} or
 * {@link #throwOnTestConnection()} to simulate failures. The last arguments
 * received are exposed for assertions.</p>
 */
public class FakeSetupService implements SetupService {

    private LoadedSetup loaded = new LoadedSetup(new ConnectionSettings(null, null, null), false);
    private boolean throwOnTestConnection;
    private boolean throwOnSave;
    private boolean throwOnProvision;
    private boolean hasAdmin;

    private ConnectionSettings lastSaved;
    private ConnectionSettings lastTested;
    private String lastAdminName;
    private String lastAdminUsername;
    private String lastAdminPassword;

    public FakeSetupService withLoaded(ConnectionSettings settings) {
        this.loaded = new LoadedSetup(settings, false);
        return this;
    }

    /** Simulates a locked or unsupported keyring while loading. */
    public FakeSetupService withKeyringUnavailable() {
        this.loaded = new LoadedSetup(loaded.settings(), true);
        return this;
    }

    public FakeSetupService withAdmin(boolean hasAdmin) {
        this.hasAdmin = hasAdmin;
        return this;
    }

    public FakeSetupService throwOnTestConnection() {
        this.throwOnTestConnection = true;
        return this;
    }

    public FakeSetupService throwOnSave() {
        this.throwOnSave = true;
        return this;
    }

    public FakeSetupService throwOnProvision() {
        this.throwOnProvision = true;
        return this;
    }

    public ConnectionSettings lastSaved() {
        return lastSaved;
    }

    public ConnectionSettings lastTested() {
        return lastTested;
    }

    public String lastAdminName() {
        return lastAdminName;
    }

    public String lastAdminUsername() {
        return lastAdminUsername;
    }

    public String lastAdminPassword() {
        return lastAdminPassword;
    }

    @Override
    public LoadedSetup loadSetup() {
        return loaded;
    }

    @Override
    public void testConnection(ConnectionSettings settings) throws SetupException {
        lastTested = settings;
        if (throwOnTestConnection) {
            throw new SetupException("Simulated connection failure");
        }
    }

    @Override
    public void save(ConnectionSettings settings) throws SetupException {
        lastSaved = settings;
        if (throwOnSave) {
            throw new SetupException("Simulated save failure");
        }
    }

    @Override
    public void provision(ConnectionSettings settings, String adminName, String adminUsername,
                          String adminPassword) throws SetupException {
        lastSaved = settings;
        lastAdminName = adminName;
        lastAdminUsername = adminUsername;
        lastAdminPassword = adminPassword;
        if (throwOnProvision) {
            throw new SetupException("Simulated provision failure");
        }
    }

    @Override
    public boolean hasAdminUser() {
        return hasAdmin;
    }
}
