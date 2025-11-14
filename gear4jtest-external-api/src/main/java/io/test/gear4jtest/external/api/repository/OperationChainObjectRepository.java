package io.test.gear4jtest.external.api.repository;

import java.util.List;
import java.util.Optional;

import io.test.gear4jtest.external.api.ExecutionMode;
import io.test.gear4jtest.external.api.model.OperationChainObject;

public interface OperationChainObjectRepository {
    long insert(OperationChainObject obj);

    Optional<OperationChainObject> find(String assemblyLineId, String version, ExecutionMode mode);

    Optional<OperationChainObject> findLatestRun(String assemblyLineId);

    boolean exists(String assemblyLineId, String version, ExecutionMode mode);

    List<OperationChainObject> findAll(String assemblyLineId);
}
