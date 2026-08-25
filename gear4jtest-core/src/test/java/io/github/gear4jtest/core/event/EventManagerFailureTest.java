package io.github.gear4jtest.core.event;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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

            manager.shutdown();
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
    void dispatch_shouldCountEveryRejectedReactionWhenRepeatedLogsAreSuppressed() throws Exception {
        // Given
        ExecutorService executor = mock(ExecutorService.class);
        doThrow(new RejectedExecutionException("saturated")).when(executor).execute(any(Runnable.class));
        EventManager manager = eventManager(executor);
        int eventCount = 20;

        try {
            // When
            for (int index = 0; index < eventCount; index++) {
                manager.publish(new Event("pipe", UUID.randomUUID(), "REJECTED_" + index));
            }

            // Then
            manager.shutdown();
            EventRuntimeStats stats = manager.snapshotStats();
            assertThat(stats.publishedEvents()).isEqualTo(eventCount);
            assertThat(stats.dispatchedEvents()).isEqualTo(eventCount);
            assertThat(stats.droppedReactions()).isEqualTo(eventCount);
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

            manager.shutdown();
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
    void dispatch_shouldEvaluateUserPredicateOnReactionExecutor() throws Exception {
        // Given
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable,
                "predicate-reaction-executor"));
        AtomicReference<String> predicateThread = new AtomicReference<>();
        CountDownLatch reactionRan = new CountDownLatch(1);
        EventHandlingDefinition definition = EventHandlingDefinition.builder()
                .subscription(EventSubscription.on(Event.class, event -> {
                    predicateThread.set(Thread.currentThread().getName());
                    return true;
                }, event -> reactionRan.countDown()))
                .runtimeConfiguration(EventHandlingDefinition.RuntimeConfiguration.builder()
                        .sharedReactionExecutor(executor)
                        .shutdownTimeout(Duration.ofSeconds(2))
                        .build())
                .build();
        EventManager manager = new EventManager(definition, new ExecutionContextRegistry());

        try {
            // When
            manager.publish(new Event("pipe", UUID.randomUUID(), "PREDICATE_THREAD"));

            // Then
            assertThat(reactionRan.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(predicateThread.get()).isEqualTo("predicate-reaction-executor");
        } finally {
            manager.shutdown();
            executor.shutdownNow();
        }
    }

    @Test
    void dispatch_shouldIsolateFailingPredicateAndContinueOtherSubscriptions() throws Exception {
        // Given
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch healthyReactionRan = new CountDownLatch(1);
        EventHandlingDefinition definition = EventHandlingDefinition.builder()
                .subscription(EventSubscription.on(Event.class, event -> {
                    throw new IllegalStateException("predicate failed");
                }, event -> {
                }))
                .subscription(EventSubscription.on(Event.class, event -> healthyReactionRan.countDown()))
                .runtimeConfiguration(EventHandlingDefinition.RuntimeConfiguration.builder()
                        .sharedReactionExecutor(executor)
                        .shutdownTimeout(Duration.ofSeconds(2))
                        .build())
                .build();
        EventManager manager = new EventManager(definition, new ExecutionContextRegistry());

        try {
            // When
            manager.publish(new Event("pipe", UUID.randomUUID(), "FAILING_PREDICATE"));

            // Then
            assertThat(healthyReactionRan.await(2, TimeUnit.SECONDS)).isTrue();
            manager.shutdown();
            EventRuntimeStats stats = manager.snapshotStats();
            assertThat(stats.failedReactions()).isEqualTo(1L);
            assertThat(stats.completedReactions()).isEqualTo(2L);
        } finally {
            manager.shutdown();
            executor.shutdownNow();
        }
    }

    private static EventManager eventManager(ExecutorService executor) {
        EventHandlingDefinition definition = EventHandlingDefinition.builder().on(Event.class, event -> {
        }).runtimeConfiguration(EventHandlingDefinition.RuntimeConfiguration.builder()
                .sharedReactionExecutor(executor).shutdownTimeout(Duration.ofSeconds(2)).build()).build();
        return new EventManager(definition, new ExecutionContextRegistry());
    }

}
