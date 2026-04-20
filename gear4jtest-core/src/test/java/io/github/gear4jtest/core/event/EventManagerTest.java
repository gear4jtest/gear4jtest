package io.github.gear4jtest.core.event;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.gear4jtest.core.api.config.EventHandlingDefinition;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class EventManagerTest {

    @Test
    void publish_shouldDispatchOnlyMatchingSubscriptions() throws Exception {
        CopyOnWriteArrayList<String> handled = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(2);

        EventHandlingDefinition definition = EventHandlingDefinition.builder()
                .subscription(EventSubscription.on(StationStartedEvent.class, event -> {
                    handled.add("started:" + event.getOperationId());
                    latch.countDown();
                }))
                .subscription(EventSubscription.on(ParameterResolvedEvent.class, event -> {
                    handled.add("param:" + event.getParameterDescriptor());
                    latch.countDown();
                }))
                .runtimeConfiguration(EventHandlingDefinition.RuntimeConfiguration.builder()
                        .reactionExecutorFactory(Executors::newSingleThreadExecutor)
                        .shutdownTimeout(Duration.ofSeconds(2))
                        .build())
                .build();

        EventManager manager = new EventManager(definition, new ExecutionContextRegistry());
        UUID executionId = UUID.randomUUID();

        try {
            manager.publish(new StationStartedEvent(
                    "pipe", executionId, UUID.randomUUID(), "step-1", null, "item-1", "input"));
            manager.publish(new ParameterResolvedEvent(
                    "pipe",
                    executionId,
                    UUID.randomUUID(),
                    "step-1",
                    null,
                    "item-1",
                    "customer",
                    false,
                    String.class.getName()));
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
        CountDownLatch completed = new CountDownLatch(1);
        List<String> handled = new CopyOnWriteArrayList<>();

        EventHandlingDefinition definition = EventHandlingDefinition.builder()
                .on(Event.class, event -> {
                    started.countDown();
                    Thread.sleep(150);
                    handled.add(event.getName());
                    completed.countDown();
                })
                .runtimeConfiguration(EventHandlingDefinition.RuntimeConfiguration.builder()
                        .reactionExecutorFactory(Executors::newSingleThreadExecutor)
                        .shutdownTimeout(Duration.ofSeconds(2))
                        .build())
                .build();

        EventManager manager = new EventManager(definition, new ExecutionContextRegistry());
        try {
            manager.publish(new Event("pipe", UUID.randomUUID(), "FIRST"));
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();

            manager.shutdown();

            assertThat(completed.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(handled).containsExactly("FIRST");
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void shutdown_shouldSupportDetachAndDrain() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        EventHandlingDefinition definition = EventHandlingDefinition.builder()
                .on(Event.class, event -> {
                    started.countDown();
                    assertThat(release.await(2, TimeUnit.SECONDS)).isTrue();
                })
                .runtimeConfiguration(EventHandlingDefinition.RuntimeConfiguration.builder()
                        .reactionExecutorFactory(Executors::newSingleThreadExecutor)
                        .shutdownTimeout(Duration.ofSeconds(2))
                        .shutdownMode(EventHandlingDefinition.RuntimeConfiguration.ShutdownMode.DETACH_AND_DRAIN)
                        .build())
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
    void publish_shouldDoNothingWhenDefinitionHasNoSubscriptions() {
        EventManager manager = new EventManager(EventHandlingDefinition.builder().build(), new ExecutionContextRegistry());
        manager.publish(new Event("pipe", UUID.randomUUID(), "IGNORED"));
        manager.shutdown();
        assertThat(true).isTrue();
    }
    @Test
    void snapshotStats_shouldExposeDroppedAndFailedReactionsUnderSaturation() throws Exception {
        CountDownLatch firstReactionStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstReaction = new CountDownLatch(1);

        ThreadPoolExecutor sharedExecutor = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1),
                new ThreadPoolExecutor.AbortPolicy());

        EventHandlingDefinition definition = EventHandlingDefinition.builder()
                .on(Event.class, event -> {
                    if ("BLOCK".equals(event.getName())) {
                        firstReactionStarted.countDown();
                        assertThat(releaseFirstReaction.await(2, TimeUnit.SECONDS)).isTrue();
                        return;
                    }
                    if ("FAIL".equals(event.getName())) {
                        throw new IllegalStateException("boom");
                    }
                })
                .runtimeConfiguration(EventHandlingDefinition.RuntimeConfiguration.builder()
                        .sharedReactionExecutor(sharedExecutor)
                        .shutdownTimeout(Duration.ofSeconds(2))
                        .build())
                .build();

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

}
