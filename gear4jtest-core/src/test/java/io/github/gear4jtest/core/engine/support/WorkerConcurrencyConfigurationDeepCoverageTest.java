package io.github.gear4jtest.core.engine.support;

import java.time.Duration;

import io.github.gear4jtest.core.api.config.WorkerConcurrencyConfiguration;
import io.github.gear4jtest.core.api.config.WorkerConcurrencyPolicy;
import io.github.gear4jtest.core.api.config.WorkerConcurrencyRegistryConfiguration;
import io.github.gear4jtest.core.api.config.WorkerLockAcquisitionPolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkerConcurrencyConfigurationDeepCoverageTest {
    @Test
    void defaultsAndWithers_shouldCreateIndependentConfigurations() {
        // Given
        WorkerConcurrencyRegistryConfiguration registry = new WorkerConcurrencyRegistryConfiguration(3, 5, 10);

        // When
        WorkerConcurrencyConfiguration defaults = WorkerConcurrencyConfiguration.defaults();
        WorkerConcurrencyConfiguration customized = defaults
                .withConcurrencyPolicy(WorkerConcurrencyPolicy.ALLOW_PARALLEL_INVOCATIONS)
                .withLockAcquisitionPolicy(WorkerLockAcquisitionPolicy.FAIL_FAST)
                .withLockWaitTimeout(Duration.ofMillis(250))
                .withRegistryConfiguration(registry);

        // Then
        assertThat(defaults.concurrencyPolicy()).isEqualTo(WorkerConcurrencyPolicy.LOCK_PER_WORKER_INSTANCE);
        assertThat(defaults.lockAcquisitionPolicy()).isEqualTo(WorkerLockAcquisitionPolicy.BLOCK_CALLER);
        assertThat(customized.concurrencyPolicy()).isEqualTo(WorkerConcurrencyPolicy.ALLOW_PARALLEL_INVOCATIONS);
        assertThat(customized.lockAcquisitionPolicy()).isEqualTo(WorkerLockAcquisitionPolicy.FAIL_FAST);
        assertThat(customized.lockWaitTimeout()).isEqualTo(Duration.ofMillis(250));
        assertThat(customized.registryConfiguration()).isSameAs(registry);
    }

    @Test
    void constructor_shouldRejectNullsAndNonPositiveTimeouts() {
        WorkerConcurrencyRegistryConfiguration registry = WorkerConcurrencyRegistryConfiguration.defaults();

        assertThatThrownBy(() -> new WorkerConcurrencyConfiguration(null,
                WorkerLockAcquisitionPolicy.BLOCK_CALLER, Duration.ofSeconds(1), registry))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("concurrencyPolicy must not be null");
        assertThatThrownBy(() -> new WorkerConcurrencyConfiguration(WorkerConcurrencyPolicy.LOCK_PER_WORKER_INSTANCE,
                null, Duration.ofSeconds(1), registry))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("lockAcquisitionPolicy must not be null");
        assertThatThrownBy(() -> new WorkerConcurrencyConfiguration(WorkerConcurrencyPolicy.LOCK_PER_WORKER_INSTANCE,
                WorkerLockAcquisitionPolicy.BLOCK_CALLER, null, registry))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("lockWaitTimeout must not be null");
        assertThatThrownBy(() -> new WorkerConcurrencyConfiguration(WorkerConcurrencyPolicy.LOCK_PER_WORKER_INSTANCE,
                WorkerLockAcquisitionPolicy.BLOCK_CALLER, Duration.ZERO, registry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("lockWaitTimeout must be strictly positive");
        assertThatThrownBy(() -> new WorkerConcurrencyConfiguration(WorkerConcurrencyPolicy.LOCK_PER_WORKER_INSTANCE,
                WorkerLockAcquisitionPolicy.BLOCK_CALLER, Duration.ofMillis(-1), registry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("lockWaitTimeout must be strictly positive");
        assertThatThrownBy(() -> new WorkerConcurrencyConfiguration(WorkerConcurrencyPolicy.LOCK_PER_WORKER_INSTANCE,
                WorkerLockAcquisitionPolicy.BLOCK_CALLER, Duration.ofSeconds(1), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("registryConfiguration must not be null");
    }
}
