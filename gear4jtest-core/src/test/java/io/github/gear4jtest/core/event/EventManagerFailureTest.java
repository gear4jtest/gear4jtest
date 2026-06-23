package io.github.gear4jtest.core.event;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import io.github.gear4jtest.core.api.config.EventHandlingDefinition;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class EventManagerFailureTest {
    @Test
    void constructor_shouldAcceptNullDefinitionAsEmptyRuntime() {
        EventManager manager = new EventManager(null, new ExecutionContextRegistry());

        EventManager.ShutdownHandle handle = manager.shutdown();

        assertThat(handle.detached()).isFalse();
        assertThat(handle.completion()).isCompleted();
        assertThat(manager.snapshotStats().publishedEvents()).isZero();
        assertThat(manager.snapshotStats().remainingEventQueueCapacity()).isPositive();
    }

    @Test
    void publish_shouldRejectNullEvents() {
        EventManager manager = new EventManager(EventHandlingDefinition.builder().build(),
                new ExecutionContextRegistry());

        assertThatNullPointerException().isThrownBy(() -> manager.publish(null)).withMessage("event");
    }

    @Test
    void publish_shouldIgnoreEventsAfterShutdown() {
        ExecutorService executor = mock(ExecutorService.class);
        EventManager manager = new EventManager(EventHandlingDefinition.builder().on(Event.class, event -> {
        }).runtimeConfiguration(EventHandlingDefinition.RuntimeConfiguration.builder()
                .sharedReactionExecutor(executor).shutdownTimeout(Duration.ofMillis(100)).build()).build(),
                new ExecutionContextRegistry());

        manager.shutdown();
        manager.publish(new Event("pipe", UUID.randomUUID(), "IGNORED"));

        assertThat(manager.snapshotStats().publishedEvents()).isZero();
    }

    @Test
    void dispatch_shouldCountRejectedReactionSubmissionsAsDropped() throws Exception {
        ExecutorService executor = mock(ExecutorService.class);
        doThrow(new RejectedExecutionException("saturated")).when(executor).execute(any(Runnable.class));
        EventManager manager = eventManager(executor);

        try {
            manager.publish(new Event("pipe", UUID.randomUUID(), "REJECTED"));

            awaitStats(manager, stats -> stats.droppedReactions() == 1);
            EventRuntimeStats stats = manager.snapshotStats();
            assertThat(stats.publishedEvents()).isEqualTo(1L);
            assertThat(stats.dispatchedEvents()).isEqualTo(1L);
            assertThat(stats.submittedReactions()).isZero();
            assertThat(stats.droppedReactions()).isEqualTo(1L);
            assertThat(stats.pendingReactions()).isZero();
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void dispatch_shouldCountUnexpectedSubmissionFailuresAsDropped() throws Exception {
        ExecutorService executor = mock(ExecutorService.class);
        doThrow(new IllegalStateException("executor broken")).when(executor).execute(any(Runnable.class));
        EventManager manager = eventManager(executor);

        try {
            manager.publish(new Event("pipe", UUID.randomUUID(), "BROKEN_EXECUTOR"));

            awaitStats(manager, stats -> stats.droppedReactions() == 1);
            EventRuntimeStats stats = manager.snapshotStats();
            assertThat(stats.publishedEvents()).isEqualTo(1L);
            assertThat(stats.dispatchedEvents()).isEqualTo(1L);
            assertThat(stats.submittedReactions()).isZero();
            assertThat(stats.droppedReactions()).isEqualTo(1L);
            assertThat(stats.pendingReactions()).isZero();
        } finally {
            manager.shutdown();
        }
    }

    private static EventManager eventManager(ExecutorService executor) {
        EventHandlingDefinition definition = EventHandlingDefinition.builder().on(Event.class, event -> {
        }).runtimeConfiguration(EventHandlingDefinition.RuntimeConfiguration.builder()
                .sharedReactionExecutor(executor).shutdownTimeout(Duration.ofSeconds(2)).build()).build();
        return new EventManager(definition, new ExecutionContextRegistry());
    }

    private static void awaitStats(EventManager manager, java.util.function.Predicate<EventRuntimeStats> predicate)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (!predicate.test(manager.snapshotStats()) && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(predicate.test(manager.snapshotStats())).isTrue();
    }
}
