package com.entidaddesconocida.monte_erp.infrastructure.config;

import java.util.HashMap;
import java.util.Map;

/**
 * In-memory double of {@link CredentialStore} for unit tests.
 */
public class FakeCredentialStore implements CredentialStore {

    private final Map<String, String> values = new HashMap<>();
    private boolean throwOnSet;

    public FakeCredentialStore with(String account, String value) {
        values.put(account, value);
        return this;
    }

    public FakeCredentialStore throwOnSet() {
        this.throwOnSet = true;
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
        if (throwOnSet) {
            throw new CredentialStoreException("Simulated keyring failure");
        }
        values.put(account, value);
    }
}
