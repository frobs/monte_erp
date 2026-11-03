package com.entidaddesconocida.monte_erp.infrastructure.config;

import com.github.javakeyring.Keyring;

/**
 * {@link CredentialStore} backed by the {@code java-keyring} library: it stores
 * the database credentials in the operating system keyring (Windows Credential
 * Manager, macOS Keychain or Linux Secret Service / KWallet).
 *
 * <p>Reads are lenient: if no backend is available the value is reported as
 * missing ({@code null}), so the onboarding screen can be shown. Writes are not
 * silent, because the user must know that their credentials were not saved.</p>
 */
public final class KeyringCredentialStore implements CredentialStore {

    private static final String DEFAULT_SERVICE = "monte_erp";

    private final String service;

    public KeyringCredentialStore() {
        this(DEFAULT_SERVICE);
    }

    public KeyringCredentialStore(String service) {
        this.service = service;
    }

    @Override
    public String get(String account) {
        try (Keyring keyring = Keyring.create()) {
            return keyring.getPassword(service, account);
        } catch (Exception e) {
            // Missing entry, locked keyring or unsupported backend: treat as absent.
            return null;
        }
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
