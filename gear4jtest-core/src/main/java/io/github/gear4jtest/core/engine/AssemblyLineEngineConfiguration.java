package io.github.gear4jtest.core.engine;

import java.time.Duration;

import io.github.gear4jtest.core.engine.support.WorkerConcurrencyConfiguration;
import io.github.gear4jtest.core.engine.support.WorkerConcurrencyManager;
import io.github.gear4jtest.core.engine.support.WorkerConcurrencyPolicy;
import io.github.gear4jtest.core.engine.support.WorkerConcurrencyRegistryConfiguration;
import io.github.gear4jtest.core.engine.support.WorkerLockAcquisitionPolicy;

final class AssemblyLineEngineConfiguration {
    private AssemblyLineEngineConfiguration() {
    }

    static WorkerConcurrencyConfiguration effectiveWorkerConcurrencyConfiguration(
                                                                                  WorkerConcurrencyConfiguration configuredConcurrency,
                                                                                  WorkerConcurrencyPolicy configuredPolicy,
                                                                                  WorkerLockAcquisitionPolicy configuredLockAcquisitionPolicy,
                                                                                  Duration configuredLockWaitTimeout,
                                                                                  WorkerConcurrencyRegistryConfiguration configuredRegistry,
                                                                                  WorkerConcurrencyManager configuredManager) {
        WorkerConcurrencyConfiguration configuration = configuredConcurrency != null
                ? configuredConcurrency
                : WorkerConcurrencyConfiguration.defaults();

        if (configuredPolicy != null) {
            configuration = configuration.withConcurrencyPolicy(configuredPolicy);
        } else if (configuredConcurrency == null && configuredManager != null) {
            configuration = configuration
                    .withConcurrencyPolicy(WorkerConcurrencyPolicy.ENGINE_LOCAL_LOCK_PER_WORKER_INSTANCE);
        }

        if (configuredLockAcquisitionPolicy != null) {
            configuration = configuration.withLockAcquisitionPolicy(configuredLockAcquisitionPolicy);
        }
        if (configuredLockWaitTimeout != null) {
            configuration = configuration.withLockWaitTimeout(configuredLockWaitTimeout);
        }
        if (configuredRegistry != null) {
            configuration = configuration.withRegistryConfiguration(configuredRegistry);
        }
        return configuration;
    }

    static WorkerConcurrencyManager defaultWorkerConcurrencyManager(WorkerConcurrencyConfiguration configuration) {
        return switch (configuration.concurrencyPolicy()) {
            case LOCK_PER_WORKER_INSTANCE -> WorkerConcurrencyManager.global();
            case ENGINE_LOCAL_LOCK_PER_WORKER_INSTANCE, LOCK_REUSED_WORKER_INSTANCE_ONLY, ALLOW_PARALLEL_INVOCATIONS ->
                new WorkerConcurrencyManager(configuration.registryConfiguration());
        };
    }
}
