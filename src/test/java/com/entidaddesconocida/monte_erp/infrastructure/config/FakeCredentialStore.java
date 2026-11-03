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
    private boolean throwOnSet;

    public FakeCredentialStore with(String account, String value) {
        values.put(account, value);
        return this;
    }

    public FakeCredentialStore throwOnSet() {
        this.throwOnSet = true;
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
    public String get(String account) {
        return values.get(account);
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
