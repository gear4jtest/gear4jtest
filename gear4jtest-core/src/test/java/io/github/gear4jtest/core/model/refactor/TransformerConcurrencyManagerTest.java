package io.github.gear4jtest.core.model.refactor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TransformerConcurrencyManagerTest {

    @Test
    void guardFor_shouldReturnSameGuardForSameTransformerInstance() {
        TransformerConcurrencyManager manager = new TransformerConcurrencyManager();
        Object transformer = new Object();

        TransformerConcurrencyGuard first =
                manager.guardFor(transformer, TransformerConcurrencyStrategy.FAIL_FAST);
        TransformerConcurrencyGuard second =
                manager.guardFor(transformer, TransformerConcurrencyStrategy.BLOCK_CALLER);

        // Même instance car même clé dans le map
        assertThat(first).isSameAs(second);
    }

    @Test
    void guardFor_shouldReturnDifferentGuardForDifferentTransformers() {
        TransformerConcurrencyManager manager = new TransformerConcurrencyManager();
        Object t1 = new Object();
        Object t2 = new Object();

        TransformerConcurrencyGuard g1 =
                manager.guardFor(t1, TransformerConcurrencyStrategy.FAIL_FAST);
        TransformerConcurrencyGuard g2 =
                manager.guardFor(t2, TransformerConcurrencyStrategy.FAIL_FAST);

        assertThat(g1).isNotSameAs(g2);
    }

    @Test
    void guardFor_shouldRecreateGuardAfterClear() {
        TransformerConcurrencyManager manager = new TransformerConcurrencyManager();
        Object transformer = new Object();

        TransformerConcurrencyGuard first =
                manager.guardFor(transformer, TransformerConcurrencyStrategy.FAIL_FAST);

        manager.clear();

        TransformerConcurrencyGuard second =
                manager.guardFor(transformer, TransformerConcurrencyStrategy.FAIL_FAST);

        assertThat(second).isNotSameAs(first);
    }

    @Test
    void guardFor_shouldRejectNullArguments() {
        TransformerConcurrencyManager manager = new TransformerConcurrencyManager();

        assertThatThrownBy(() -> manager.guardFor(null, TransformerConcurrencyStrategy.FAIL_FAST))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> manager.guardFor(new Object(), null))
                .isInstanceOf(NullPointerException.class);
    }
}
