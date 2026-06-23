package io.github.gear4jtest.core.engine;

import java.time.Duration;

import io.github.gear4jtest.core.engine.support.WorkerConcurrencyConfiguration;
import io.github.gear4jtest.core.engine.support.WorkerConcurrencyManager;
import io.github.gear4jtest.core.engine.support.WorkerConcurrencyPolicy;
import io.github.gear4jtest.core.engine.support.WorkerConcurrencyRegistryConfiguration;
import io.github.gear4jtest.core.engine.support.WorkerLockAcquisitionPolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AssemblyLineEngineConfigurationDeepCoverageTest {
    @Test
    void effectiveWorkerConcurrencyConfiguration_shouldStartFromDefaultsAndUseEngineLocalWhenCustomManagerIsProvided() {
        // Given
        WorkerConcurrencyManager customManager = new WorkerConcurrencyManager();

        // When
        WorkerConcurrencyConfiguration configuration = AssemblyLineEngineConfiguration
                .effectiveWorkerConcurrencyConfiguration(null, null, null, null, null, customManager);

        // Then
        assertThat(configuration.concurrencyPolicy())
                .isEqualTo(WorkerConcurrencyPolicy.ENGINE_LOCAL_LOCK_PER_WORKER_INSTANCE);
        assertThat(configuration.lockAcquisitionPolicy()).isEqualTo(WorkerLockAcquisitionPolicy.BLOCK_CALLER);
        assertThat(configuration.lockWaitTimeout()).isEqualTo(WorkerConcurrencyConfiguration.DEFAULT_LOCK_WAIT_TIMEOUT);
    }

    @Test
    void effectiveWorkerConcurrencyConfiguration_shouldApplyExplicitOverridesOverConfiguredBase() {
        // Given
        WorkerConcurrencyRegistryConfiguration registry = new WorkerConcurrencyRegistryConfiguration(2, 10, 20);
        WorkerConcurrencyConfiguration base = WorkerConcurrencyConfiguration.defaults()
                .withConcurrencyPolicy(WorkerConcurrencyPolicy.ALLOW_PARALLEL_INVOCATIONS);
        Duration timeout = Duration.ofSeconds(3);

        // When
        WorkerConcurrencyConfiguration configuration = AssemblyLineEngineConfiguration
                .effectiveWorkerConcurrencyConfiguration(base,
                                                         WorkerConcurrencyPolicy.ENGINE_LOCAL_LOCK_PER_WORKER_INSTANCE,
                                                         WorkerLockAcquisitionPolicy.FAIL_FAST,
                                                         timeout,
                                                         registry,
                                                         null);

        // Then
        assertThat(configuration.concurrencyPolicy())
                .isEqualTo(WorkerConcurrencyPolicy.ENGINE_LOCAL_LOCK_PER_WORKER_INSTANCE);
        assertThat(configuration.lockAcquisitionPolicy()).isEqualTo(WorkerLockAcquisitionPolicy.FAIL_FAST);
        assertThat(configuration.lockWaitTimeout()).isEqualTo(timeout);
        assertThat(configuration.registryConfiguration()).isSameAs(registry);
    }

    @Test
    void defaultWorkerConcurrencyManager_shouldUseGlobalOnlyForProcessWideLocking() {
        // Given
        WorkerConcurrencyConfiguration processWide = WorkerConcurrencyConfiguration.defaults()
                .withConcurrencyPolicy(WorkerConcurrencyPolicy.LOCK_PER_WORKER_INSTANCE);
        WorkerConcurrencyConfiguration local = WorkerConcurrencyConfiguration.defaults()
                .withConcurrencyPolicy(WorkerConcurrencyPolicy.ENGINE_LOCAL_LOCK_PER_WORKER_INSTANCE);
        WorkerConcurrencyConfiguration none = WorkerConcurrencyConfiguration.defaults()
                .withConcurrencyPolicy(WorkerConcurrencyPolicy.ALLOW_PARALLEL_INVOCATIONS);

        // When / Then
        assertThat(AssemblyLineEngineConfiguration.defaultWorkerConcurrencyManager(processWide))
                .isSameAs(WorkerConcurrencyManager.global());
        assertThat(AssemblyLineEngineConfiguration.defaultWorkerConcurrencyManager(local))
                .isNotSameAs(WorkerConcurrencyManager.global());
        assertThat(AssemblyLineEngineConfiguration.defaultWorkerConcurrencyManager(none))
                .isNotSameAs(WorkerConcurrencyManager.global());
    }
}
