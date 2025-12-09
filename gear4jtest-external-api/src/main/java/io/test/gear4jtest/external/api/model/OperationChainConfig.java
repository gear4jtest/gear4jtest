package io.test.gear4jtest.external.api.model;

import java.util.Map;

import io.test.gear4jtest.external.api.StoreType;

import static java.util.Objects.requireNonNull;

public final class OperationChainConfig {
    private final String alId;
    private final Boolean allowRunPublicationWithoutTest;
    private final StoreType storeType;
    private final Map<String, String> storeProps;

    public OperationChainConfig(String alId, Boolean allowRunPublicationWithoutTest, StoreType storeType, Map<String, String> storeProps) {
        this.alId = alId;
        this.allowRunPublicationWithoutTest = allowRunPublicationWithoutTest;
        this.storeType = requireNonNull(storeType, "storeType");
        this.storeProps = Map.copyOf(requireNonNull(storeProps, "storeProps"));
    }

    public String alId() {
        return alId;
    }

    public Boolean allowRunPublicationWithoutTest() {
        return allowRunPublicationWithoutTest;
    }

    public StoreType storeType() {
        return storeType;
    }

    public Map<String, String> storeProps() {
        return storeProps;
    }
}
