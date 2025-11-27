package io.github.gear4jtest.core.model.refactor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class TransformerConcurrencyGuardTest {

    @Test
    void failFast_shouldThrowOnConcurrentUseFromAnotherThread() throws Exception {
        TransformerConcurrencyGuard guard =
                new TransformerConcurrencyGuard(TransformerConcurrencyStrategy.FAIL_FAST);

        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        Thread t1 = new Thread(() -> {
            guard.beforeUse();
            locked.countDown();
            try {
                // On garde le lock pendant un petit moment
                release.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                guard.afterUse();
            }
        });

        t1.start();

        // On attend que le premier thread ait bien pris le lock
        locked.await(2, TimeUnit.SECONDS);

        // Ici on est dans un autre thread que t1 -> tryLock doit échouer
        assertThatThrownBy(guard::beforeUse)
                .isInstanceOf(ConcurrentTransformerUseException.class)
                .hasMessageContaining("Transformer is already in use");

        // On libère le premier thread proprement
        release.countDown();

        t1.join();
    }

    @Test
    void blockCaller_shouldBlockUntilLockIsReleased() throws Exception {
        TransformerConcurrencyGuard guard =
                new TransformerConcurrencyGuard(TransformerConcurrencyStrategy.BLOCK_CALLER);

        CountDownLatch locked = new CountDownLatch(1);

        Thread t1 = new Thread(() -> {
            guard.beforeUse();
            locked.countDown();
            try {
                // garde le lock ~200ms
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                guard.afterUse();
            }
        });

        t1.start();

        locked.await(2, TimeUnit.SECONDS);

        long before = System.nanoTime();
        // On est dans un autre thread que t1, lock.lock() doit bloquer
        guard.beforeUse();
        long after = System.nanoTime();
        guard.afterUse();

        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(after - before);

        // On tolère large pour éviter les flaky tests
        assertThat(elapsedMillis)
                .as("BLOCK_CALLER should block at least ~150ms")
                .isGreaterThanOrEqualTo(150);

        t1.join();
    }

    @Test
    void ignore_shouldNotLockAndNeverThrow() {
        TransformerConcurrencyGuard guard =
                new TransformerConcurrencyGuard(TransformerConcurrencyStrategy.IGNORE);

        // Appels répétés, aucune exception attendue
        for (int i = 0; i < 1000; i++) {
            guard.beforeUse();
            guard.afterUse();
        }

        // Si on arrive ici, le test est OK (aucune exception)
        assertThat(true).isTrue();
    }
}
