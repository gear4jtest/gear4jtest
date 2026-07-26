package io.github.gear4jtest.core.event;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.gear4jtest.core.api.config.EventHandlingDefinition;
import io.github.gear4jtest.core.util.MonotonicDeadline;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EventRuntimeShutdownTest {
    @Test
    void awaitCompletion_shouldCancelAndStopOwnedExecutorAtDeadline() {
        // Given
        ExecutorService executor = Executors.newSingleThreadExecutor();
        var configuration = EventHandlingDefinition.RuntimeConfiguration.builder()
                .shutdownTimeout(Duration.ofMillis(20))
                .build();
        EventRuntimeShutdown shutdown = EventRuntimeShutdown.active(configuration, executor, true);
        AtomicInteger cancellations = new AtomicInteger();
        long startedNanos = System.nanoTime();

        // When
        shutdown.awaitCompletion(new CompletableFuture<>(),
                                 MonotonicDeadline.start(configuration.getShutdownTimeout()),
                                 cancellations::incrementAndGet);

        // Then
        assertThat(cancellations).hasValue(1);
        assertThat(executor.isShutdown()).isTrue();
        assertThat(Duration.ofNanos(System.nanoTime() - startedNanos)).isLessThan(Duration.ofSeconds(1));
    }

    @Test
    void dispatchDrain_shouldNotStopCallerManagedExecutor() {
        // Given
        ExecutorService executor = Executors.newSingleThreadExecutor();
        var configuration = EventHandlingDefinition.RuntimeConfiguration.builder()
                .shutdownTimeout(Duration.ofSeconds(1))
                .build();
        EventRuntimeShutdown shutdown = EventRuntimeShutdown.active(configuration, executor, false);

        try {
            // When
            shutdown.initiateOwnedExecutorShutdownAfterDispatchDrain();
            shutdown.awaitCompletion(CompletableFuture.completedFuture(null),
                                     MonotonicDeadline.start(configuration.getShutdownTimeout()), () -> {
                                     });

            // Then
            assertThat(executor.isShutdown()).isFalse();
        } finally {
            executor.shutdownNow();
        }
    }
}
