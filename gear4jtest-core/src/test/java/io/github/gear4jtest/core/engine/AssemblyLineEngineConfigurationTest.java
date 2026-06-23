package io.github.gear4jtest.core.engine;

import java.time.Duration;
import java.util.stream.Stream;

import io.github.gear4jtest.core.engine.support.WorkerConcurrencyConfiguration;
import io.github.gear4jtest.core.engine.support.WorkerConcurrencyManager;
import io.github.gear4jtest.core.engine.support.WorkerConcurrencyPolicy;
import io.github.gear4jtest.core.engine.support.WorkerConcurrencyRegistryConfiguration;
import io.github.gear4jtest.core.engine.support.WorkerLockAcquisitionPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

class AssemblyLineEngineConfigurationTest {
    private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(3);
    private static final WorkerConcurrencyRegistryConfiguration TINY_REGISTRY = new WorkerConcurrencyRegistryConfiguration(
            2, 10, 20);

    @ParameterizedTest(name = "{0}")
    @MethodSource("workerConcurrencyResolutionScenarios")
    void effectiveWorkerConcurrencyConfiguration_shouldApplyDocumentedPrecedence(Scenario scenario) {
        // When
        WorkerConcurrencyConfiguration configuration = AssemblyLineEngineConfiguration
                .effectiveWorkerConcurrencyConfiguration(scenario.configuredConcurrency,
                                                         scenario.configuredPolicy,
                                                         scenario.configuredLockAcquisitionPolicy,
                                                         scenario.configuredLockWaitTimeout,
                                                         scenario.configuredRegistry,
                                                         scenario.configuredManager);

        // Then
        assertThat(configuration.concurrencyPolicy()).isEqualTo(scenario.expectedPolicy);
        assertThat(configuration.lockAcquisitionPolicy()).isEqualTo(scenario.expectedLockAcquisitionPolicy);
        assertThat(configuration.lockWaitTimeout()).isEqualTo(scenario.expectedLockWaitTimeout);
        assertThat(configuration.registryConfiguration()).isEqualTo(scenario.expectedRegistry);
    }

    @Test
    void defaultWorkerConcurrencyManager_shouldUseGlobalOnlyForProcessWideLocking() {
        // Given
        WorkerConcurrencyConfiguration processWide = WorkerConcurrencyConfiguration.defaults()
                .withConcurrencyPolicy(WorkerConcurrencyPolicy.LOCK_PER_WORKER_INSTANCE);
        WorkerConcurrencyConfiguration local = WorkerConcurrencyConfiguration.defaults()
                .withConcurrencyPolicy(WorkerConcurrencyPolicy.ENGINE_LOCAL_LOCK_PER_WORKER_INSTANCE);
        WorkerConcurrencyConfiguration reusedOnly = WorkerConcurrencyConfiguration.defaults()
                .withConcurrencyPolicy(WorkerConcurrencyPolicy.LOCK_REUSED_WORKER_INSTANCE_ONLY);
        WorkerConcurrencyConfiguration none = WorkerConcurrencyConfiguration.defaults()
                .withConcurrencyPolicy(WorkerConcurrencyPolicy.ALLOW_PARALLEL_INVOCATIONS);

        // When / Then
        assertThat(AssemblyLineEngineConfiguration.defaultWorkerConcurrencyManager(processWide))
                .isSameAs(WorkerConcurrencyManager.global());
        assertThat(AssemblyLineEngineConfiguration.defaultWorkerConcurrencyManager(local))
                .isNotSameAs(WorkerConcurrencyManager.global());
        assertThat(AssemblyLineEngineConfiguration.defaultWorkerConcurrencyManager(reusedOnly))
                .isNotSameAs(WorkerConcurrencyManager.global());
        assertThat(AssemblyLineEngineConfiguration.defaultWorkerConcurrencyManager(none))
                .isNotSameAs(WorkerConcurrencyManager.global());
    }

    private static Stream<Scenario> workerConcurrencyResolutionScenarios() {
        WorkerConcurrencyConfiguration defaults = WorkerConcurrencyConfiguration.defaults();
        WorkerConcurrencyConfiguration configuredBase = defaults
                .withConcurrencyPolicy(WorkerConcurrencyPolicy.ALLOW_PARALLEL_INVOCATIONS)
                .withLockAcquisitionPolicy(WorkerLockAcquisitionPolicy.FAIL_FAST)
                .withLockWaitTimeout(Duration.ofSeconds(30));

        return Stream.of(
                         new Scenario("defaults when nothing is configured",
                                 null,
                                 null,
                                 null,
                                 null,
                                 null,
                                 null,
                                 WorkerConcurrencyPolicy.LOCK_PER_WORKER_INSTANCE,
                                 WorkerLockAcquisitionPolicy.BLOCK_CALLER,
                                 WorkerConcurrencyConfiguration.DEFAULT_LOCK_WAIT_TIMEOUT,
                                 WorkerConcurrencyRegistryConfiguration.defaults()),
                         new Scenario("custom manager without explicit config switches to engine-local locking",
                                 null,
                                 null,
                                 null,
                                 null,
                                 null,
                                 new WorkerConcurrencyManager(),
                                 WorkerConcurrencyPolicy.ENGINE_LOCAL_LOCK_PER_WORKER_INSTANCE,
                                 WorkerLockAcquisitionPolicy.BLOCK_CALLER,
                                 WorkerConcurrencyConfiguration.DEFAULT_LOCK_WAIT_TIMEOUT,
                                 WorkerConcurrencyRegistryConfiguration.defaults()),
                         new Scenario("explicit concurrency config wins over custom manager fallback",
                                 configuredBase,
                                 null,
                                 null,
                                 null,
                                 null,
                                 new WorkerConcurrencyManager(),
                                 WorkerConcurrencyPolicy.ALLOW_PARALLEL_INVOCATIONS,
                                 WorkerLockAcquisitionPolicy.FAIL_FAST,
                                 Duration.ofSeconds(30),
                                 WorkerConcurrencyRegistryConfiguration.defaults()),
                         new Scenario("explicit policy overrides configured base",
                                 configuredBase,
                                 WorkerConcurrencyPolicy.ENGINE_LOCAL_LOCK_PER_WORKER_INSTANCE,
                                 null,
                                 null,
                                 null,
                                 null,
                                 WorkerConcurrencyPolicy.ENGINE_LOCAL_LOCK_PER_WORKER_INSTANCE,
                                 WorkerLockAcquisitionPolicy.FAIL_FAST,
                                 Duration.ofSeconds(30),
                                 WorkerConcurrencyRegistryConfiguration.defaults()),
                         new Scenario("explicit lock-reused-only policy overrides configured base",
                                 configuredBase,
                                 WorkerConcurrencyPolicy.LOCK_REUSED_WORKER_INSTANCE_ONLY,
                                 null,
                                 null,
                                 null,
                                 null,
                                 WorkerConcurrencyPolicy.LOCK_REUSED_WORKER_INSTANCE_ONLY,
                                 WorkerLockAcquisitionPolicy.FAIL_FAST,
                                 Duration.ofSeconds(30),
                                 WorkerConcurrencyRegistryConfiguration.defaults()),
                         new Scenario("explicit lock acquisition timeout and registry override configured base",
                                 configuredBase,
                                 null,
                                 WorkerLockAcquisitionPolicy.BLOCK_CALLER,
                                 SHORT_TIMEOUT,
                                 TINY_REGISTRY,
                                 null,
                                 WorkerConcurrencyPolicy.ALLOW_PARALLEL_INVOCATIONS,
                                 WorkerLockAcquisitionPolicy.BLOCK_CALLER,
                                 SHORT_TIMEOUT,
                                 TINY_REGISTRY));
    }

    private record Scenario(String name,
                            WorkerConcurrencyConfiguration configuredConcurrency,
                            WorkerConcurrencyPolicy configuredPolicy,
                            WorkerLockAcquisitionPolicy configuredLockAcquisitionPolicy,
                            Duration configuredLockWaitTimeout,
                            WorkerConcurrencyRegistryConfiguration configuredRegistry,
                            WorkerConcurrencyManager configuredManager,
                            WorkerConcurrencyPolicy expectedPolicy,
                            WorkerLockAcquisitionPolicy expectedLockAcquisitionPolicy,
                            Duration expectedLockWaitTimeout,
                            WorkerConcurrencyRegistryConfiguration expectedRegistry) {
        @Override
        public String toString() {
            return name;
        }
    }
}
