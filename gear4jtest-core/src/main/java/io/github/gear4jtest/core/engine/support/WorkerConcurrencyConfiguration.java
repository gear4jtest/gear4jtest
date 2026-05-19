package io.github.gear4jtest.core.engine.support;

import java.time.Duration;
import java.util.Objects;

/**
 * Runtime configuration for protecting stateful worker instances from unsafe
 * concurrent invocations.
 */
public record WorkerConcurrencyConfiguration(WorkerConcurrencyPolicy concurrencyPolicy,
                                             WorkerLockAcquisitionPolicy lockAcquisitionPolicy,
                                             Duration lockWaitTimeout,
                                             WorkerConcurrencyRegistryConfiguration registryConfiguration) {

    public static final Duration DEFAULT_LOCK_WAIT_TIMEOUT = Duration.ofMinutes(1);

    public WorkerConcurrencyConfiguration {
        Objects.requireNonNull(concurrencyPolicy, "concurrencyPolicy must not be null");
        Objects.requireNonNull(lockAcquisitionPolicy, "lockAcquisitionPolicy must not be null");
        Objects.requireNonNull(lockWaitTimeout, "lockWaitTimeout must not be null");
        Objects.requireNonNull(registryConfiguration, "registryConfiguration must not be null");
        if (lockWaitTimeout.isZero() || lockWaitTimeout.isNegative()) {
            throw new IllegalArgumentException("lockWaitTimeout must be strictly positive");
        }
    }

    public static WorkerConcurrencyConfiguration defaults() {
        return new WorkerConcurrencyConfiguration(WorkerConcurrencyPolicy.LOCK_PER_WORKER_INSTANCE,
                WorkerLockAcquisitionPolicy.BLOCK_CALLER,
                DEFAULT_LOCK_WAIT_TIMEOUT,
                WorkerConcurrencyRegistryConfiguration.defaults());
    }

    public WorkerConcurrencyConfiguration withConcurrencyPolicy(WorkerConcurrencyPolicy value) {
        return new WorkerConcurrencyConfiguration(value, lockAcquisitionPolicy, lockWaitTimeout, registryConfiguration);
    }

    public WorkerConcurrencyConfiguration withLockAcquisitionPolicy(WorkerLockAcquisitionPolicy value) {
        return new WorkerConcurrencyConfiguration(concurrencyPolicy, value, lockWaitTimeout, registryConfiguration);
    }

    public WorkerConcurrencyConfiguration withLockWaitTimeout(Duration value) {
        return new WorkerConcurrencyConfiguration(concurrencyPolicy, lockAcquisitionPolicy, value,
                registryConfiguration);
    }

    public WorkerConcurrencyConfiguration withRegistryConfiguration(WorkerConcurrencyRegistryConfiguration value) {
        return new WorkerConcurrencyConfiguration(concurrencyPolicy, lockAcquisitionPolicy, lockWaitTimeout, value);
    }
}
