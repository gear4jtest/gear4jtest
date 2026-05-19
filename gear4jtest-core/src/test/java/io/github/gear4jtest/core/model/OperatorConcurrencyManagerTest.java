package io.github.gear4jtest.core.model;

import io.github.gear4jtest.core.engine.support.WorkerConcurrencyGuard;
import io.github.gear4jtest.core.engine.support.WorkerConcurrencyManager;
import io.github.gear4jtest.core.engine.support.WorkerConcurrencyRegistryConfiguration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OperatorConcurrencyManagerTest {
    @Test
    void guardFor_shouldReturnSameGuardForSameWorkerInstance() {
        WorkerConcurrencyManager manager = new WorkerConcurrencyManager();
        Object worker = new Object();

        WorkerConcurrencyGuard first = manager.guardFor(worker);
        WorkerConcurrencyGuard second = manager.guardFor(worker);

        assertThat(first).isSameAs(second);
    }

    @Test
    void guardFor_shouldReturnDifferentGuardForDifferentWorkerInstancesEvenWhenEqualsMatches() {
        WorkerConcurrencyManager manager = new WorkerConcurrencyManager();
        EqualWorker firstWorker = new EqualWorker();
        EqualWorker secondWorker = new EqualWorker();

        WorkerConcurrencyGuard first = manager.guardFor(firstWorker);
        WorkerConcurrencyGuard second = manager.guardFor(secondWorker);

        assertThat(firstWorker).isEqualTo(secondWorker);
        assertThat(first).isNotSameAs(second);
    }

    @Test
    void guardFor_shouldRecreateGuardAfterClear() {
        WorkerConcurrencyManager manager = new WorkerConcurrencyManager();
        Object worker = new Object();

        WorkerConcurrencyGuard first = manager.guardFor(worker);

        manager.clear();

        WorkerConcurrencyGuard second = manager.guardFor(worker);

        assertThat(second).isNotSameAs(first);
    }

    @Test
    void guardFor_shouldRejectNullWorker() {
        WorkerConcurrencyManager manager = new WorkerConcurrencyManager();

        assertThatThrownBy(() -> manager.guardFor(null)).isInstanceOf(NullPointerException.class)
                .hasMessageContaining("worker must not be null");
    }

    @Test
    void guardFor_shouldFailFastWhenTrackedWorkerGuardrailIsExceeded() {
        WorkerConcurrencyManager manager = new WorkerConcurrencyManager(
                new WorkerConcurrencyRegistryConfiguration(1, 1, 2));

        manager.guardFor(new Object());

        assertThatThrownBy(() -> manager.guardFor(new Object())).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Too many worker instances tracked");
    }

    private static final class EqualWorker {
        @Override
        public boolean equals(Object obj) {
            return obj instanceof EqualWorker;
        }

        @Override
        public int hashCode() {
            return 42;
        }
    }
}
