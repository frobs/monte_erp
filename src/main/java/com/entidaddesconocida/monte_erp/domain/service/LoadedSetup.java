package com.entidaddesconocida.monte_erp.domain.service;

import com.entidaddesconocida.monte_erp.domain.model.ConnectionSettings;

/**
 * Persisted connection settings together with what is known about the
 * credential store.
 *
 * <p>Without it, a locked or unsupported keyring looked exactly like a fresh
 * installation: the setup screen offered a clean onboarding while the user's
 * credentials were still (or no longer) stored (S4).</p>
 *
 * @param settings               values that could be read; missing ones are
 *                               {@code null}, never a half-filled object
 * @param credentialsUnavailable {@code true} when the credential store could not
 *                               be read, so a missing user or password must not
 *                               be interpreted as "not configured"
 */
public record LoadedSetup(ConnectionSettings settings, boolean credentialsUnavailable) {

    public LoadedSetup {
        settings = settings == null ? new ConnectionSettings(null, null, null) : settings;
    }
}
