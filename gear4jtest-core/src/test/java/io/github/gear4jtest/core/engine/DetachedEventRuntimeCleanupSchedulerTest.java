package io.github.gear4jtest.core.engine;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
}
