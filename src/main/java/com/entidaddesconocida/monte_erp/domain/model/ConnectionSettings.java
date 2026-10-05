package com.entidaddesconocida.monte_erp.domain.model;

/**
 * Database connection settings entered by the user during onboarding or when
 * changing the database configuration.
 *
 * <p>This is a plain value object: the URL is persisted in a properties file
 * next to the application and the credentials are stored in the operating
 * system keyring. Missing values are represented with {@code null} so
 * {@link #isComplete()} can decide whether the onboarding screen must be shown.</p>
 *
 * @param url      JDBC URL of the database
 * @param user     database login name
 * @param password database password
 */
public record ConnectionSettings(String url, String user, String password) {

    /**
     * {@code true} when the URL, the user and the password are all present.
     *
     * <p>An empty (blank) value counts as missing, which matches the startup
     * rule: if any of the three is null or empty the configuration screen is
     * shown instead of the login screen.</p>
     */
    public boolean isComplete() {
        return isNotBlank(url) && isNotBlank(user) && isNotBlank(password);
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
