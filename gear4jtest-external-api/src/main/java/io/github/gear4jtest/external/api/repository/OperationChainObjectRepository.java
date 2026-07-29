package io.github.gear4jtest.external.api.repository;

import java.util.List;
import java.util.Optional;

import io.github.gear4jtest.core.persistence.PageRequest;
import io.github.gear4jtest.external.api.ExecutionMode;
import io.github.gear4jtest.external.api.model.OperationChainObject;

public interface OperationChainObjectRepository {
    long insert(OperationChainObject obj);

    Optional<OperationChainObject> find(String assemblyLineId, String version, ExecutionMode mode);

    Optional<OperationChainObject> findLatestRun(String assemblyLineId);

    boolean exists(String assemblyLineId, String version, ExecutionMode mode);

    /**
     * Finds a bounded, zero-based page of operation-chain objects for one assembly
     * line.
     *
     * <p>
     * Implementations must apply the page before materializing results. In
     * particular, a persistence-backed repository must translate the request into
     * backend-level pagination rather than loading every version and slicing it in
     * memory.
     * </p>
     *
     * @param assemblyLineId assembly-line identifier whose versions are listed
     * @param pageRequest    required bounded page
     * @return the requested page in reverse publication order
     */
    List<OperationChainObject> findAll(String assemblyLineId, PageRequest pageRequest);
}
