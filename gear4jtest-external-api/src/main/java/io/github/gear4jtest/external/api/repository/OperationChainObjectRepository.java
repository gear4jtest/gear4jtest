package io.github.gear4jtest.external.api.repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.github.gear4jtest.core.persistence.PageRequest;
import io.github.gear4jtest.external.api.ExecutionMode;
import io.github.gear4jtest.external.api.model.OperationChainObject;

public interface OperationChainObjectRepository {
    long insert(OperationChainObject obj);

    Optional<OperationChainObject> find(String assemblyLineId, String version, ExecutionMode mode);

    Optional<OperationChainObject> findLatestRun(String assemblyLineId);

    boolean exists(String assemblyLineId, String version, ExecutionMode mode);

    List<OperationChainObject> findAll(String assemblyLineId);

    /**
     * Finds a bounded, zero-based page of operation-chain objects for one assembly
     * line.
     *
     * <p>
     * JDBC implementations override this method with SQL-level pagination. The
     * default implementation keeps source compatibility for custom repositories,
     * but callers expecting large result sets should prefer implementations with
     * native pagination.
     * </p>
     */
    default List<OperationChainObject> findAll(String assemblyLineId, PageRequest pageRequest) {
        Objects.requireNonNull(pageRequest, "pageRequest must not be null");
        return findAll(assemblyLineId).stream()
                .skip(pageRequest.offset())
                .limit(pageRequest.limit())
                .toList();
    }
}
