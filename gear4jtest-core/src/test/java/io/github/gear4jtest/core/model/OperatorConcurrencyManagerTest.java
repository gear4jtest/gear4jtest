package io.github.gear4jtest.core.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.gear4jtest.core.engine.support.WorkerConcurrencyGuard;
import io.github.gear4jtest.core.engine.support.WorkerConcurrencyManager;
import io.github.gear4jtest.core.engine.support.WorkerConcurrencyStrategy;
import org.junit.jupiter.api.Test;

class OperatorConcurrencyManagerTest {

    @Test
    void guardFor_shouldReturnSameGuardForSameTransformerInstance() {
        WorkerConcurrencyManager manager = new WorkerConcurrencyManager();
        Object transformer = new Object();

        WorkerConcurrencyGuard first =
                manager.guardFor(transformer, WorkerConcurrencyStrategy.FAIL_FAST);
        WorkerConcurrencyGuard second =
                manager.guardFor(transformer, WorkerConcurrencyStrategy.BLOCK_CALLER);

        // Même instance car même clé dans le map
        assertThat(first).isSameAs(second);
    }

    @Test
    void guardFor_shouldReturnDifferentGuardForDifferentTransformers() {
        WorkerConcurrencyManager manager = new WorkerConcurrencyManager();
        Object t1 = new Object();
        Object t2 = new Object();

        WorkerConcurrencyGuard g1 =
                manager.guardFor(t1, WorkerConcurrencyStrategy.FAIL_FAST);
        WorkerConcurrencyGuard g2 =
                manager.guardFor(t2, WorkerConcurrencyStrategy.FAIL_FAST);

        assertThat(g1).isNotSameAs(g2);
    }

    @Test
    void guardFor_shouldRecreateGuardAfterClear() {
        WorkerConcurrencyManager manager = new WorkerConcurrencyManager();
        Object transformer = new Object();

        WorkerConcurrencyGuard first =
                manager.guardFor(transformer, WorkerConcurrencyStrategy.FAIL_FAST);

        manager.clear();

        WorkerConcurrencyGuard second =
                manager.guardFor(transformer, WorkerConcurrencyStrategy.FAIL_FAST);

        assertThat(second).isNotSameAs(first);
    }

    @Test
    void guardFor_shouldRejectNullArguments() {
        WorkerConcurrencyManager manager = new WorkerConcurrencyManager();

        assertThatThrownBy(() -> manager.guardFor(null, WorkerConcurrencyStrategy.FAIL_FAST))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> manager.guardFor(new Object(), null))
                .isInstanceOf(NullPointerException.class);
    }
}
