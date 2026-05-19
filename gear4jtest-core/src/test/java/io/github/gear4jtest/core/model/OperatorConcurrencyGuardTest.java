package io.github.gear4jtest.core.model;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.github.gear4jtest.core.engine.support.WorkerConcurrencyGuard;
import io.github.gear4jtest.core.engine.support.WorkerLockAcquisitionPolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OperatorConcurrencyGuardTest {
    @Test
    void beforeUse_shouldBlockUntilLockIsReleased() throws Exception {
        WorkerConcurrencyGuard guard = new WorkerConcurrencyGuard();

        CountDownLatch locked = new CountDownLatch(1);

        Thread t1 = new Thread(() -> {
            guard.beforeUse();
            locked.countDown();
            try {
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
        guard.beforeUse();
        long after = System.nanoTime();
        guard.afterUse();

        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(after - before);

        assertThat(elapsedMillis).as("guard should block at least ~150ms").isGreaterThanOrEqualTo(150);

        t1.join();
    }

    @Test
    void beforeUse_shouldFailFastWhenLockIsAlreadyHeld() throws Exception {
        WorkerConcurrencyGuard guard = new WorkerConcurrencyGuard();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        guard.beforeUse();

        try {
            Thread contender = new Thread(() -> {
                try {
                    guard.beforeUse(WorkerLockAcquisitionPolicy.FAIL_FAST);
                    guard.afterUse();
                } catch (Throwable e) {
                    failure.set(e);
                }
            });

            contender.start();
            contender.join(2_000);

            assertThat(failure.get()).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Worker lock is already held");
        } finally {
            guard.afterUse();
        }
    }

    @Test
    void beforeUse_shouldTimeoutWhenBlockCallerWaitsTooLong() throws Exception {
        WorkerConcurrencyGuard guard = new WorkerConcurrencyGuard();

        guard.beforeUse();

        try {
            long before = System.nanoTime();

            assertThatThrownBy(() -> guard.beforeUse(WorkerLockAcquisitionPolicy.BLOCK_CALLER, Duration.ofMillis(50)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Timed out after PT0.05S while waiting for worker lock");

            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - before);
            assertThat(elapsedMillis).isGreaterThanOrEqualTo(40);
        } finally {
            guard.afterUse();
        }
    }

}
