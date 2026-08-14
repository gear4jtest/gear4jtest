package io.github.gear4jtest.core.engine;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DetachedEventRuntimeCleanupSchedulerTest {
    @Test
    void schedule_shouldPreserveSubMillisecondTimeout() throws InterruptedException {
        // Given
        CountDownLatch cleanup = new CountDownLatch(1);
        CompletableFuture<Void> completion = new CompletableFuture<>();

        // When
        new DetachedEventRuntimeCleanupScheduler().schedule(cleanup::countDown, completion, Duration.ofNanos(1L));

        // Then
        assertThat(cleanup.await(2, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void schedule_shouldCancelPendingTimeoutAfterEarlyCompletion() throws InterruptedException {
        // Given
        ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1);
        scheduler.setRemoveOnCancelPolicy(true);
        CountDownLatch cleanup = new CountDownLatch(1);
        CompletableFuture<Void> completion = new CompletableFuture<>();

        try {
            new DetachedEventRuntimeCleanupScheduler(scheduler)
                    .schedule(cleanup::countDown, completion, Duration.ofHours(1L));
            assertThat(scheduler.getQueue()).hasSize(1);

            // When
            completion.complete(null);

            // Then
            assertThat(cleanup.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(scheduler.getQueue()).isEmpty();
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    void schedule_shouldRunCleanupOnlyOnceWhenCompletionRacesTimeout() throws InterruptedException {
        // Given
        CountDownLatch cleanup = new CountDownLatch(1);
        AtomicInteger cleanups = new AtomicInteger();
        CompletableFuture<Void> completion = new CompletableFuture<>();

        // When
        new DetachedEventRuntimeCleanupScheduler().schedule(() -> {
            cleanups.incrementAndGet();
            cleanup.countDown();
        }, completion, Duration.ofNanos(1L));
        completion.complete(null);

        // Then
        assertThat(cleanup.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(cleanups).hasValue(1);
    }

    @Test
    void schedule_shouldCleanImmediatelyWhenTimeoutSchedulingIsRejected() throws InterruptedException {
        // Given
        ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1);
        scheduler.shutdownNow();
        CountDownLatch cleanup = new CountDownLatch(1);

        // When
        new DetachedEventRuntimeCleanupScheduler(scheduler)
                .schedule(cleanup::countDown, new CompletableFuture<>(), Duration.ofSeconds(1L));

        // Then
        assertThat(cleanup.await(2, TimeUnit.SECONDS)).isTrue();
    }
}
