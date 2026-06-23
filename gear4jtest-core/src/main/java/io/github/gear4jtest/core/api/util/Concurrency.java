package io.github.gear4jtest.core.api.util;

import java.time.Duration;

import io.github.gear4jtest.core.api.config.ParallelExecutionConfiguration;
import io.github.gear4jtest.core.engine.support.WorkerConcurrencyConfiguration;
import io.github.gear4jtest.core.engine.support.WorkerConcurrencyPolicy;
import io.github.gear4jtest.core.engine.support.WorkerLockAcquisitionPolicy;

/**
 * Shortcuts for engine-level concurrency configuration.
 */
public final class Concurrency {
    private Concurrency() {
    }

    public static ParallelExecutionConfiguration parallelExecutionDefaults() {
        return ParallelExecutionConfiguration.defaults();
    }

    public static ParallelExecutionConfiguration parallelExecutionWithDefaultAwaitTimeout(Duration timeout) {
        return ParallelExecutionConfiguration.withDefaultAwaitTimeout(timeout);
    }

    public static WorkerConcurrencyConfiguration workerConcurrencyDefaults() {
        return WorkerConcurrencyConfiguration.defaults();
    }

    public static WorkerConcurrencyConfiguration allowParallelWorkerInvocations() {
        return WorkerConcurrencyConfiguration.defaults()
                .withConcurrencyPolicy(WorkerConcurrencyPolicy.ALLOW_PARALLEL_INVOCATIONS);
    }

    public static WorkerConcurrencyConfiguration processWideWorkerLocks() {
        return WorkerConcurrencyConfiguration.defaults()
                .withConcurrencyPolicy(WorkerConcurrencyPolicy.LOCK_PER_WORKER_INSTANCE);
    }

    public static WorkerConcurrencyConfiguration engineLocalWorkerLocks() {
        return WorkerConcurrencyConfiguration.defaults()
                .withConcurrencyPolicy(WorkerConcurrencyPolicy.ENGINE_LOCAL_LOCK_PER_WORKER_INSTANCE);
    }

    public static WorkerConcurrencyConfiguration reusedWorkerLocksOnly() {
        return WorkerConcurrencyConfiguration.defaults()
                .withConcurrencyPolicy(WorkerConcurrencyPolicy.LOCK_REUSED_WORKER_INSTANCE_ONLY);
    }

    public static WorkerConcurrencyConfiguration failFastWorkerLocks() {
        return WorkerConcurrencyConfiguration.defaults()
                .withLockAcquisitionPolicy(WorkerLockAcquisitionPolicy.FAIL_FAST);
    }
}
