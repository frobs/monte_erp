package com.entidaddesconocida.monte_erp.domain.service;

import com.entidaddesconocida.monte_erp.domain.model.ConnectionSettings;

/**
 * Port that abstracts the persistence of the database configuration and the
 * provisioning of the database during onboarding.
 *
 * <p>Implementations live in {@code infrastructure} (e.g.
 * {@code DatabaseSetupService}), which knows where the URL lives (a properties
 * file next to the application), where the credentials live (the OS keyring)
 * and how to run the migrations and create the administrator user.</p>
 */
public interface SetupService {

    /**
     * Returns the configuration currently persisted, with {@code null} for any
     * value that is missing or could not be read.
     */
    ConnectionSettings load();

    /**
     * Verifies that the given settings can open a JDBC connection without
     * persisting anything.
     *
     * @throws SetupException when the connection cannot be established
     */
    void testConnection(ConnectionSettings settings) throws SetupException;

    /**
     * Persists the settings (URL to the properties file, credentials to the
     * keyring) and points the connection factory at them.
     *
     * @throws SetupException when the settings cannot be written
     */
    void save(ConnectionSettings settings) throws SetupException;

    /**
     * Persists the settings, applies the pending migrations and creates the
     * administrator user so the application can be used right after.
     *
     * @param adminName     administrator display name
     * @param adminUsername administrator login name
     * @param adminPassword administrator password (stored as a PBKDF2 hash)
     * @throws SetupException when any step fails
     */
    void provision(ConnectionSettings settings, String adminName, String adminUsername,
                   String adminPassword) throws SetupException;

    /**
     * {@code true} when the database already contains at least one administrator
     * user. Used at startup to decide whether onboarding is still required.
     *
     * @throws SetupException when the check cannot be performed
     */
    boolean hasAdminUser() throws SetupException;
}
