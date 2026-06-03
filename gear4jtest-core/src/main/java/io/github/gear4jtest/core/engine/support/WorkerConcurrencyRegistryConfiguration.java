package io.github.gear4jtest.core.engine.support;

/**
 * Memory-safety guardrails for the worker concurrency registry.
 */
public record WorkerConcurrencyRegistryConfiguration(int purgeEveryAcquisitions,
                                                     int warnWhenTrackedWorkersExceeds,
                                                     int failWhenTrackedWorkersExceeds) {

    private static final int DEFAULT_PURGE_EVERY_ACQUISITIONS = 256;
    private static final int DEFAULT_WARN_WHEN_TRACKED_WORKERS_EXCEEDS = 10_000;
    private static final int DEFAULT_FAIL_WHEN_TRACKED_WORKERS_EXCEEDS = 100_000;

    public WorkerConcurrencyRegistryConfiguration {
        if (purgeEveryAcquisitions <= 0) {
            throw new IllegalArgumentException("purgeEveryAcquisitions must be greater than 0");
        }
        if (warnWhenTrackedWorkersExceeds <= 0) {
            throw new IllegalArgumentException("warnWhenTrackedWorkersExceeds must be greater than 0");
        }
        if (failWhenTrackedWorkersExceeds <= warnWhenTrackedWorkersExceeds) {
            throw new IllegalArgumentException(
                    "failWhenTrackedWorkersExceeds must be greater than warnWhenTrackedWorkersExceeds");
        }
    }

    public static WorkerConcurrencyRegistryConfiguration defaults() {
        return new WorkerConcurrencyRegistryConfiguration(DEFAULT_PURGE_EVERY_ACQUISITIONS,
                DEFAULT_WARN_WHEN_TRACKED_WORKERS_EXCEEDS,
                DEFAULT_FAIL_WHEN_TRACKED_WORKERS_EXCEEDS);
    }
}
