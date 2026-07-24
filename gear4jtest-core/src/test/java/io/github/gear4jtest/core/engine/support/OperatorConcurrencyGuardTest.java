package io.github.gear4jtest.core.engine.support;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.github.gear4jtest.core.api.config.WorkerLockAcquisitionPolicy;
import io.github.gear4jtest.core.exception.ConcurrentTransformerUseException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OperatorConcurrencyGuardTest {
    @Test
    void beforeUse_shouldBlockUntilLockIsReleased() throws InterruptedException {
        WorkerConcurrencyGuard guard = new WorkerConcurrencyGuard();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch attemptStarted = new CountDownLatch(1);
        CountDownLatch contenderAcquired = new CountDownLatch(1);

        Thread holder = new Thread(() -> {
            guard.beforeUse();
            locked.countDown();
            try {
                release.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                guard.afterUse();
            }
        });
        holder.start();
        assertThat(locked.await(2, TimeUnit.SECONDS)).isTrue();

        Thread contender = new Thread(() -> {
            attemptStarted.countDown();
            guard.beforeUse();
            try {
                contenderAcquired.countDown();
            } finally {
                guard.afterUse();
            }
        });
        contender.start();
        assertThat(attemptStarted.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(contenderAcquired.await(100, TimeUnit.MILLISECONDS))
                .as("contender must not acquire while the holder still owns the guard")
                .isFalse();

        release.countDown();

        assertThat(contenderAcquired.await(2, TimeUnit.SECONDS))
                .as("contender should acquire once the holder releases the guard")
                .isTrue();
        holder.join(2_000);
        contender.join(2_000);
        assertThat(holder.isAlive()).isFalse();
        assertThat(contender.isAlive()).isFalse();
    }

    @Test
    void beforeUse_shouldFailFastWhenLockIsAlreadyHeld() throws InterruptedException {
        WorkerConcurrencyGuard guard = new WorkerConcurrencyGuard();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        guard.beforeUse();

        try {
            Thread contender = new Thread(() -> {
                try {
                    guard.beforeUse(WorkerLockAcquisitionPolicy.FAIL_FAST);
                    guard.afterUse();
                } catch (RuntimeException e) {
                    failure.set(e);
                }
            });

            contender.start();
            contender.join(2_000);

            assertThat(failure.get()).isInstanceOf(ConcurrentTransformerUseException.class)
                    .hasMessageContaining("Worker lock is already held");
        } finally {
            guard.afterUse();
        }
    }

    @Test
    void beforeUse_shouldTimeoutWhenBlockCallerWaitsTooLong() {
        WorkerConcurrencyGuard guard = new WorkerConcurrencyGuard();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        guard.beforeUse();

        Duration timeout = Duration.ofMillis(50);

        try {
            Thread contender = new Thread(() -> {
                try {
                    guard.beforeUse(WorkerLockAcquisitionPolicy.BLOCK_CALLER, timeout);
                } catch (RuntimeException exception) {
                    failure.set(exception);
                }
            });
            contender.start();
            contender.join(2_000);

            assertThat(contender.isAlive()).isFalse();
            assertThat(failure.get()).isInstanceOf(ConcurrentTransformerUseException.class)
                    .hasMessageContaining("Timed out after PT0.05S while waiting for worker lock");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        } finally {
            guard.afterUse();
        }
    }

    @Test
    void beforeUse_shouldFailImmediatelyOnSameThreadReentrance() {
        WorkerConcurrencyGuard guard = new WorkerConcurrencyGuard();
        guard.beforeUse();

        try {
            assertThatThrownBy(() -> guard.beforeUse(WorkerLockAcquisitionPolicy.BLOCK_CALLER,
                                                     Duration.ofMinutes(1)))
                    .isInstanceOf(ConcurrentTransformerUseException.class)
                    .hasMessage("Reentrant worker lock acquisition is not supported");
        } finally {
            guard.afterUse();
        }
    }

    @Test
    void beforeUse_shouldAcceptTimeoutLargerThanNanosecondRange() {
        // Given
        WorkerConcurrencyGuard guard = new WorkerConcurrencyGuard();

        // When
        guard.beforeUse(WorkerLockAcquisitionPolicy.BLOCK_CALLER, Duration.ofSeconds(Long.MAX_VALUE));

        // Then
        guard.afterUse();
    }

}
