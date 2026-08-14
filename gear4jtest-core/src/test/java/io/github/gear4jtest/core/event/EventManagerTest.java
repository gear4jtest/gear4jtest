package io.github.gear4jtest.core.event;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import io.github.gear4jtest.core.api.config.EventHandlingDefinition;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

@Timeout(value = 10, unit = TimeUnit.SECONDS)
class EventManagerTest {
    @Test
    void publish_shouldDispatchOnlyMatchingSubscriptions() throws Exception {
        CopyOnWriteArrayList<String> handled = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(2);

        EventHandlingDefinition definition = EventHandlingDefinition.builder()
                .subscription(EventSubscription.on(StationStartedEvent.class, event -> {
                    handled.add("started:" + event.getOperationId());
                    latch.countDown();
                })).subscription(EventSubscription.on(ParameterResolvedEvent.class, event -> {
                    handled.add("param:" + event.getParameterDescriptor());
                    latch.countDown();
                }))
                .runtimeConfiguration(EventHandlingDefinition.RuntimeConfiguration.builder()
                        .reactionExecutorFactory(Executors::newSingleThreadExecutor)
                        .shutdownTimeout(Duration.ofSeconds(2)).build())
                .build();

        EventManager manager = new EventManager(definition, new ExecutionContextRegistry());
        UUID executionId = UUID.randomUUID();

        try {
            manager.publish(new StationStartedEvent("pipe", executionId, UUID.randomUUID(), "step-1", null, "item-1",
                    "input"));
            manager.publish(new ParameterResolvedEvent("pipe", executionId, UUID.randomUUID(), "step-1", null, "item-1",
                    "customer", false, String.class.getName()));
            manager.publish(new Event("pipe", executionId));

            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(handled).containsExactlyInAnyOrder("started:step-1", "param:customer");
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void shutdown_shouldDrainAlreadyQueuedEvents() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch releaseReaction = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(1);
        List<String> handled = new CopyOnWriteArrayList<>();
        RecordingThreadPoolExecutor executor = new RecordingThreadPoolExecutor();

        EventHandlingDefinition definition = EventHandlingDefinition.builder().on(Event.class, event -> {
            started.countDown();
            releaseReaction.await();
            handled.add(event.getName());
            completed.countDown();
        }).runtimeConfiguration(EventHandlingDefinition.RuntimeConfiguration.builder()
                .reactionExecutorFactory(() -> executor).shutdownTimeout(Duration.ofSeconds(2))
                .build()).build();

        EventManager manager = new EventManager(definition, new ExecutionContextRegistry());
        try {
            manager.publish(new Event("pipe", UUID.randomUUID(), "FIRST"));
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();

            CompletableFuture<EventManager.ShutdownHandle> shutdown = CompletableFuture.supplyAsync(manager::shutdown);
            assertThat(executor.shutdownInitiated.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(shutdown.isDone()).isFalse();

            releaseReaction.countDown();
            EventManager.ShutdownHandle handle = shutdown.get(2, TimeUnit.SECONDS);

            assertThat(completed.getCount()).isZero();
            assertThat(handled).containsExactly("FIRST");
            assertThat(handle.completion()).isCompleted();
        } finally {
            releaseReaction.countDown();
            manager.shutdown();
            executor.shutdownNow();
            executor.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void shutdown_shouldSupportDetachAndDrain() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        EventHandlingDefinition definition = EventHandlingDefinition.builder().on(Event.class, event -> {
            started.countDown();
            assertThat(release.await(2, TimeUnit.SECONDS)).isTrue();
        }).runtimeConfiguration(EventHandlingDefinition.RuntimeConfiguration.builder()
                .reactionExecutorFactory(Executors::newSingleThreadExecutor).shutdownTimeout(Duration.ofSeconds(2))
                .shutdownMode(EventHandlingDefinition.RuntimeConfiguration.ShutdownMode.DETACH_AND_DRAIN).build())
                .build();

        EventManager manager = new EventManager(definition, new ExecutionContextRegistry());
        manager.publish(new Event("pipe", UUID.randomUUID(), "FIRST"));
        assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();

        EventManager.ShutdownHandle handle = manager.shutdown();

        assertThat(handle.detached()).isTrue();
        assertThat(handle.completion().isDone()).isFalse();

        release.countDown();
        handle.completion().join();
        assertThat(handle.completion()).isCompleted();
    }

    @Test
    void shutdown_shouldCompleteWhenCancelModeDropsQueuedReactionBeforeStart() throws Exception {
        CountDownLatch firstReactionStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstReaction = new CountDownLatch(1);
        AtomicBoolean secondReactionRan = new AtomicBoolean(false);

        ThreadPoolExecutor sharedExecutor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>());

        EventHandlingDefinition definition = EventHandlingDefinition.builder()
                .subscription(EventSubscription.on(Event.class, event -> {
                    firstReactionStarted.countDown();
                    assertThat(releaseFirstReaction.await(2, TimeUnit.SECONDS)).isTrue();
                }))
                .subscription(EventSubscription.on(Event.class, event -> secondReactionRan.set(true)))
                .runtimeConfiguration(EventHandlingDefinition.RuntimeConfiguration.builder()
                        .sharedReactionExecutor(sharedExecutor)
                        .shutdownMode(EventHandlingDefinition.RuntimeConfiguration.ShutdownMode.CANCEL_PENDING_TASKS)
                        .shutdownTimeout(Duration.ofSeconds(2)).build())
                .build();

        EventManager manager = new EventManager(definition, new ExecutionContextRegistry());
        try {
            manager.publish(new Event("pipe", UUID.randomUUID(), "CANCEL"));

            assertThat(firstReactionStarted.await(2, TimeUnit.SECONDS)).isTrue();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (manager.snapshotStats().submittedReactions() < 2 && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            assertThat(manager.snapshotStats().submittedReactions()).isEqualTo(2);

            CompletableFuture<EventManager.ShutdownHandle> shutdown = CompletableFuture.supplyAsync(manager::shutdown);
            try {
                // Wait until shutdown has actually marked the queued task as dropped before
                // releasing the running one.
                // Otherwise the single-thread executor may legitimately run the second reaction
                // first.
                deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                while (manager.snapshotStats().droppedReactions() < 1 && System.nanoTime() < deadline) {
                    Thread.sleep(10);
                }
                assertThat(manager.snapshotStats().droppedReactions()).isEqualTo(1);
            } finally {
                releaseFirstReaction.countDown();
            }
            shutdown.get(2, TimeUnit.SECONDS);

            EventRuntimeStats stats = manager.snapshotStats();
            assertThat(secondReactionRan).isFalse();
            assertThat(stats.submittedReactions()).isEqualTo(2);
            assertThat(stats.completedReactions()).isEqualTo(1);
            assertThat(stats.droppedReactions()).isEqualTo(1);
        } finally {
            releaseFirstReaction.countDown();
            manager.shutdown();
            sharedExecutor.shutdownNow();
        }
    }

    @Test
    void publish_shouldDropEventsWhenEventQueueIsFull() throws Exception {
        CountDownLatch executeStarted = new CountDownLatch(1);
        CountDownLatch releaseExecute = new CountDownLatch(1);
        ExecutorService sharedExecutor = mock(ExecutorService.class);
        doAnswer(invocation -> {
            executeStarted.countDown();
            assertThat(releaseExecute.await(2, TimeUnit.SECONDS)).isTrue();
            Runnable task = invocation.getArgument(0, Runnable.class);
            task.run();
            return null;
        }).when(sharedExecutor).execute(any(Runnable.class));

        EventHandlingDefinition definition = EventHandlingDefinition.builder().on(Event.class, event -> {
        }).runtimeConfiguration(EventHandlingDefinition.RuntimeConfiguration.builder()
                .sharedReactionExecutor(sharedExecutor).eventQueueCapacity(1).shutdownTimeout(Duration.ofSeconds(2))
                .build()).build();
        EventManager manager = new EventManager(definition, new ExecutionContextRegistry());

        try {
            UUID executionId = UUID.randomUUID();
            manager.publish(new Event("pipe", executionId, "FIRST"));
            assertThat(executeStarted.await(2, TimeUnit.SECONDS)).isTrue();

            manager.publish(new Event("pipe", executionId, "SECOND"));
            manager.publish(new Event("pipe", executionId, "DROPPED"));

            EventRuntimeStats saturatedStats = manager.snapshotStats();
            assertThat(saturatedStats.publishedEvents()).isEqualTo(2);
            assertThat(saturatedStats.droppedEvents()).isEqualTo(1);
            assertThat(saturatedStats.queuedEvents()).isEqualTo(1);
            assertThat(saturatedStats.remainingEventQueueCapacity()).isZero();
        } finally {
            releaseExecute.countDown();
            manager.shutdown();
        }
    }

    @Test
    void publish_shouldDoNothingWhenDefinitionHasNoSubscriptions() {
        EventManager manager = new EventManager(EventHandlingDefinition.builder().build(),
                new ExecutionContextRegistry());
        manager.publish(new Event("pipe", UUID.randomUUID(), "IGNORED"));
        manager.shutdown();
        assertThat(true).isTrue();
    }

    @Test
    void snapshotStats_shouldExposeDroppedAndFailedReactionsUnderSaturation() throws Exception {
        CountDownLatch firstReactionStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstReaction = new CountDownLatch(1);

        ThreadPoolExecutor sharedExecutor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1), new ThreadPoolExecutor.AbortPolicy());

        EventHandlingDefinition definition = EventHandlingDefinition.builder().on(Event.class, event -> {
            if ("BLOCK".equals(event.getName())) {
                firstReactionStarted.countDown();
                assertThat(releaseFirstReaction.await(2, TimeUnit.SECONDS)).isTrue();
                return;
            }
            if ("FAIL".equals(event.getName())) {
                throw new IllegalStateException("boom");
            }
        }).runtimeConfiguration(EventHandlingDefinition.RuntimeConfiguration.builder()
                .sharedReactionExecutor(sharedExecutor).shutdownTimeout(Duration.ofSeconds(2)).build()).build();

        EventManager manager = new EventManager(definition, new ExecutionContextRegistry());
        try {
            UUID executionId = UUID.randomUUID();
            manager.publish(new Event("pipe", executionId, "BLOCK"));
            manager.publish(new Event("pipe", executionId, "FAIL"));
            manager.publish(new Event("pipe", executionId, "DROP"));

            assertThat(firstReactionStarted.await(2, TimeUnit.SECONDS)).isTrue();
            releaseFirstReaction.countDown();

            manager.shutdown();

            EventRuntimeStats stats = manager.snapshotStats();
            assertThat(stats.publishedEvents()).isEqualTo(3);
            assertThat(stats.dispatchedEvents()).isEqualTo(3);
            assertThat(stats.submittedReactions()).isEqualTo(2);
            assertThat(stats.completedReactions()).isEqualTo(2);
            assertThat(stats.droppedReactions()).isEqualTo(1);
            assertThat(stats.failedReactions()).isEqualTo(1);
        } finally {
            manager.shutdown();
            sharedExecutor.shutdownNow();
        }
    }

    @Test
    void shutdown_shouldRespectTimeoutWhenRunningReactionDoesNotComplete() throws Exception {
        CountDownLatch reactionStarted = new CountDownLatch(1);
        CountDownLatch releaseReaction = new CountDownLatch(1);

        ThreadPoolExecutor sharedExecutor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>());

        EventHandlingDefinition definition = EventHandlingDefinition.builder().on(Event.class, event -> {
            reactionStarted.countDown();
            releaseReaction.await();
        }).runtimeConfiguration(EventHandlingDefinition.RuntimeConfiguration.builder()
                .sharedReactionExecutor(sharedExecutor).shutdownTimeout(Duration.ofMillis(100)).build()).build();

        EventManager manager = new EventManager(definition, new ExecutionContextRegistry());
        try {
            manager.publish(new Event("pipe", UUID.randomUUID(), "BLOCK"));
            assertThat(reactionStarted.await(2, TimeUnit.SECONDS)).isTrue();

            CompletableFuture<EventManager.ShutdownHandle> shutdown = CompletableFuture.supplyAsync(manager::shutdown);

            assertThat(shutdown.get(1, TimeUnit.SECONDS).detached()).isFalse();
            EventRuntimeStats timeoutStats = manager.snapshotStats();
            assertThat(timeoutStats.completedReactions()).isZero();
            assertThat(timeoutStats.pendingReactions()).isEqualTo(1);
            assertThat(timeoutStats.inFlightReactions()).isEqualTo(1);
        } finally {
            releaseReaction.countDown();
            manager.shutdown();
            sharedExecutor.shutdownNow();
        }
    }

    @Test
    void shutdown_shouldUseOneDeadlineWhenOwnedReactionIgnoresInterruption() throws Exception {
        // Given
        CountDownLatch reactionStarted = new CountDownLatch(1);
        CountDownLatch interruptionObserved = new CountDownLatch(1);
        CountDownLatch releaseReaction = new CountDownLatch(1);
        RecordingThreadPoolExecutor executor = new RecordingThreadPoolExecutor();
        Duration shutdownTimeout = Duration.ofMillis(200);

        EventHandlingDefinition definition = EventHandlingDefinition.builder().on(Event.class, event -> {
            reactionStarted.countDown();
            while (releaseReaction.getCount() > 0L) {
                try {
                    releaseReaction.await();
                } catch (InterruptedException ignored) {
                    interruptionObserved.countDown();
                    // Deliberately ignore interruption to reproduce an uncooperative reaction.
                }
            }
        }).runtimeConfiguration(EventHandlingDefinition.RuntimeConfiguration.builder()
                .reactionExecutorFactory(() -> executor)
                .shutdownTimeout(shutdownTimeout)
                .build()).build();

        EventManager manager = new EventManager(definition, new ExecutionContextRegistry());
        try {
            manager.publish(new Event("pipe", UUID.randomUUID(), "BLOCK"));
            assertThat(reactionStarted.await(2, TimeUnit.SECONDS)).isTrue();

            // When
            long startedNanos = System.nanoTime();
            manager.shutdown();
            long elapsedNanos = System.nanoTime() - startedNanos;

            // Then
            assertThat(interruptionObserved.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(executor.lastAwaitTerminationNanos())
                    .as("forced executor shutdown must only receive the remaining global budget")
                    .isLessThan(TimeUnit.MILLISECONDS.toNanos(25L));
            assertThat(elapsedNanos)
                    .as("shutdown must not reopen a second complete timeout window")
                    .isLessThan(TimeUnit.MILLISECONDS.toNanos(350L));
        } finally {
            releaseReaction.countDown();
            executor.shutdownNow();
            executor.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void shutdown_shouldAcceptTimeoutLargerThanNanosecondRange() {
        // Given
        ExecutorService executor = Executors.newSingleThreadExecutor();
        EventHandlingDefinition definition = EventHandlingDefinition.builder().on(Event.class, event -> {
        }).runtimeConfiguration(EventHandlingDefinition.RuntimeConfiguration.builder()
                .reactionExecutorFactory(() -> executor)
                .shutdownTimeout(Duration.ofSeconds(Long.MAX_VALUE))
                .build()).build();
        EventManager manager = new EventManager(definition, new ExecutionContextRegistry());

        try {
            // When
            EventManager.ShutdownHandle handle = manager.shutdown();

            // Then
            assertThat(handle.detached()).isFalse();
            assertThat(handle.completion()).isCompleted();
        } finally {
            executor.shutdownNow();
        }
    }

    private static final class RecordingThreadPoolExecutor extends ThreadPoolExecutor {
        private final CountDownLatch shutdownInitiated = new CountDownLatch(1);
        private final AtomicLong lastAwaitTerminationNanos = new AtomicLong(-1L);

        private RecordingThreadPoolExecutor() {
            super(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
        }

        @Override
        public void shutdown() {
            shutdownInitiated.countDown();
            super.shutdown();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            lastAwaitTerminationNanos.set(unit.toNanos(timeout));
            return super.awaitTermination(timeout, unit);
        }

        private long lastAwaitTerminationNanos() {
            return lastAwaitTerminationNanos.get();
        }
    }

}
