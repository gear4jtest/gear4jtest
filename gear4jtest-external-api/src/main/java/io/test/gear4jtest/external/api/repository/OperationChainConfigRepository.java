package io.test.gear4jtest.external.api.repository;

import java.util.Map;
import java.util.Optional;

import io.test.gear4jtest.external.api.StoreType;
import io.test.gear4jtest.external.api.model.OperationChainConfig;

public interface OperationChainConfigRepository {
    Optional<OperationChainConfig> findByAssemblyLineId(String assemblyLineId);

    void upsert(OperationChainConfig cfg);

    void setAllowRunPublicationWithoutTest(String assemblyLineId, boolean allowed);

    void updateStore(String assemblyLineId, StoreType storeType, Map<String,String> storeProps);
}
