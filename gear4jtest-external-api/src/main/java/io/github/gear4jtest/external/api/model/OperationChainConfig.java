package io.github.gear4jtest.external.api.model;

import java.util.Map;

import io.github.gear4jtest.external.api.StoreType;

import static java.util.Objects.requireNonNull;

public record OperationChainConfig(String alId,
                                   Boolean allowRunPublicationWithoutTest,
                                   StoreType storeType,
                                   Map<String, String> storeProps) {
    public OperationChainConfig {
        alId = OperationChainModelValidation.requireText(alId, "alId", 200);
        allowRunPublicationWithoutTest = requireNonNull(allowRunPublicationWithoutTest,
                                                        "allowRunPublicationWithoutTest");
        storeType = requireNonNull(storeType, "storeType");
        storeProps = Map.copyOf(requireNonNull(storeProps, "storeProps"));
    }
}
