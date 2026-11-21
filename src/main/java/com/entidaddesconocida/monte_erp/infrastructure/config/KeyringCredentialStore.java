package com.entidaddesconocida.monte_erp.infrastructure.config;

import com.github.javakeyring.Keyring;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link CredentialStore} backed by the {@code java-keyring} library: it stores
 * the database credentials in the operating system keyring (Windows Credential
 * Manager, macOS Keychain or Linux Secret Service / KWallet).
 *
 * <p>Reads never throw, but they are not silent any more: a failed read is
 * logged at {@link Level#FINE} and reported as
 * {@link CredentialStatus#UNAVAILABLE}, so the setup screen can tell the user
 * that the keyring is unreachable instead of offering a clean onboarding as if
 * nothing was stored (S4). Writes are not silent either, because the user must
 * know that their credentials were not saved.</p>
 */
public final class KeyringCredentialStore implements CredentialStore {

    private static final Logger LOGGER = Logger.getLogger(KeyringCredentialStore.class.getName());

    private static final String DEFAULT_SERVICE = "monte_erp";

    private final String service;

    public KeyringCredentialStore() {
        this(DEFAULT_SERVICE);
    }

    public KeyringCredentialStore(String service) {
        this.service = service;
    }

    /**
     * Reads the credential, distinguishing a missing entry ({@code Missing})
     * from a backend failure ({@code Unavailable}).
     */
    @Override
    public CredentialLookup lookup(String account) {
        try (Keyring keyring = Keyring.create()) {
            String value = keyring.getPassword(service, account);
            if (value == null || value.isBlank()) {
                LOGGER.log(Level.FINE, "No keyring entry for account {0}", account);
                return CredentialLookup.missing();
            }
            return CredentialLookup.found(value);
        } catch (Exception e) {
            LOGGER.log(Level.FINE,
                    "Could not read account '" + account + "' from the keyring service '" + service + "'", e);
            return CredentialLookup.unavailable(e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : ": " + e.getMessage()));
        }
    }

    @Override
    public String get(String account) {
        return lookup(account).value();
    }

    @Override
    public void set(String account, String value) throws CredentialStoreException {
        try (Keyring keyring = Keyring.create()) {
            keyring.setPassword(service, account, value);
        } catch (Exception e) {
            throw new CredentialStoreException(
                    "No se pudo guardar la credencial en el keyring del sistema: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String account) throws CredentialStoreException {
        try (Keyring keyring = Keyring.create()) {
            keyring.deletePassword(service, account);
        } catch (Exception e) {
            throw new CredentialStoreException(
                    "No se pudo restaurar la credencial en el keyring del sistema: " + e.getMessage(), e);
        }
    }
}
