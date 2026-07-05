package io.github.gear4jtest.core.event;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.gear4jtest.core.api.config.EventHandlingDefinition;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(value = 15, unit = TimeUnit.SECONDS)
class EventManagerConcurrencyStressTest {
    @Test
    void shutdown_shouldDrainEventsPublishedConcurrentlyBeforeShutdownStarts() throws Exception {
        // Given
        int publisherCount = 8;
        int eventsPerPublisher = 75;
        int expectedEvents = publisherCount * eventsPerPublisher;
        CountDownLatch handledEvents = new CountDownLatch(expectedEvents);
        AtomicInteger handledCount = new AtomicInteger();
        ExecutorService reactionExecutor = Executors.newFixedThreadPool(4);
        ExecutorService publishers = Executors.newFixedThreadPool(publisherCount);

        EventHandlingDefinition definition = EventHandlingDefinition.builder()
                .on(Event.class, event -> {
                    handledCount.incrementAndGet();
                    handledEvents.countDown();
                })
                .runtimeConfiguration(EventHandlingDefinition.RuntimeConfiguration.builder()
                        .sharedReactionExecutor(reactionExecutor)
                        .eventQueueCapacity(expectedEvents)
                        .shutdownTimeout(Duration.ofSeconds(5))
                        .build())
                .build();
        EventManager manager = new EventManager(definition, new ExecutionContextRegistry());

        try {
            // When
            for (int publisherIndex = 0; publisherIndex < publisherCount; publisherIndex++) {
                final int publisher = publisherIndex;
                publishers.submit(() -> {
                    for (int eventIndex = 0; eventIndex < eventsPerPublisher; eventIndex++) {
                        manager.publish(new Event("stress", UUID.randomUUID(),
                                "event-" + publisher + '-' + eventIndex));
                    }
                });
            }
            publishers.shutdown();
            assertThat(publishers.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

            manager.shutdown();

            // Then
            assertThat(handledEvents.getCount()).isZero();
            assertThat(handledCount).hasValue(expectedEvents);
            EventRuntimeStats stats = manager.snapshotStats();
            assertThat(stats.publishedEvents()).isEqualTo(expectedEvents);
            assertThat(stats.dispatchedEvents()).isEqualTo(expectedEvents);
            assertThat(stats.submittedReactions()).isEqualTo(expectedEvents);
            assertThat(stats.completedReactions()).isEqualTo(expectedEvents);
            assertThat(stats.droppedEvents()).isZero();
            assertThat(stats.droppedReactions()).isZero();
            assertThat(stats.pendingReactions()).isZero();
            assertThat(stats.inFlightReactions()).isZero();
        } finally {
            manager.shutdown();
            publishers.shutdownNow();
            reactionExecutor.shutdownNow();
        }
    }
}
