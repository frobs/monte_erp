package com.entidaddesconocida.monte_erp.infrastructure.config;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * In-memory double of {@link CredentialStore} for unit tests.
 */
public class FakeCredentialStore implements CredentialStore {

    private final Map<String, String> values = new HashMap<>();
    private final Set<String> failingAccounts = new HashSet<>();
    private final Set<String> unreadableAccounts = new HashSet<>();
    private boolean throwOnSet;
    private boolean throwOnGet;

    public FakeCredentialStore with(String account, String value) {
        values.put(account, value);
        return this;
    }

    public FakeCredentialStore throwOnSet() {
        this.throwOnSet = true;
        return this;
    }

    /** Simulates a locked or unsupported keyring: every read fails. */
    public FakeCredentialStore throwOnGet() {
        this.throwOnGet = true;
        return this;
    }

    /** Simulates a backend that fails only for one account. */
    public FakeCredentialStore failOnGet(String account) {
        unreadableAccounts.add(account);
        return this;
    }

    public FakeCredentialStore failOnSet(String account) {
        failingAccounts.add(account);
        return this;
    }

    /** Raw stored value, to assert persistence without a real keyring. */
    public String stored(String account) {
        return values.get(account);
    }

    @Override
    public CredentialLookup lookup(String account) {
        if (throwOnGet || unreadableAccounts.contains(account)) {
            return CredentialLookup.unavailable("Simulated keyring backend failure");
        }
        String value = values.get(account);
        return value == null || value.isBlank()
                ? CredentialLookup.missing()
                : CredentialLookup.found(value);
    }

    @Override
    public String get(String account) {
        return lookup(account).value();
    }

    @Override
    public void set(String account, String value) {
        if (throwOnSet || failingAccounts.contains(account)) {
            throw new CredentialStoreException("Simulated keyring failure");
        }
        values.put(account, value);
    }

    @Override
    public void delete(String account) {
        values.remove(account);
    }
}
