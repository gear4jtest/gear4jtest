package io.github.gear4jtest.core.event;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import io.github.gear4jtest.core.api.config.EventHandlingDefinition;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(value = 10, unit = TimeUnit.SECONDS)
class EventManagerMultiRunLoadTest {
    @Test
    void dispatch_shouldBoundQuietRunLatencyToOneSliceUnderAsymmetricLoad() throws Exception {
        // Given
        EventDispatcher dispatcher = new EventDispatcher(1, 8);
        CountDownLatch dispatcherBlocked = new CountDownLatch(1);
        CountDownLatch releaseDispatcher = new CountDownLatch(1);
        assertThat(dispatcher.submit(() -> {
            dispatcherBlocked.countDown();
            awaitUninterruptibly(releaseDispatcher);
        })).isTrue();
        assertThat(dispatcherBlocked.await(2, TimeUnit.SECONDS)).isTrue();

        int loudEventCount = EventManager.MAX_EVENTS_PER_DISPATCH_TASK * 4;
        CountDownLatch allReactions = new CountDownLatch(loudEventCount + 1);
        CountDownLatch quietReaction = new CountDownLatch(1);
        AtomicInteger loudReactions = new AtomicInteger();
        AtomicInteger loudReactionsAtQuietDispatch = new AtomicInteger(-1);
        AtomicLong quietQueueLatencyNanos = new AtomicLong();
        ExecutorService reactionExecutor = Executors.newSingleThreadExecutor();
        EventManager loudRun = manager(dispatcher, reactionExecutor, event -> {
            loudReactions.incrementAndGet();
            allReactions.countDown();
        }, loudEventCount);
        EventManager quietRun = manager(dispatcher, reactionExecutor, event -> {
            quietQueueLatencyNanos.set(System.nanoTime() - Long.parseLong(event.getName()));
            loudReactionsAtQuietDispatch.set(loudReactions.get());
            quietReaction.countDown();
            allReactions.countDown();
        }, 1);

        try {
            for (int index = 0; index < loudEventCount; index++) {
                loudRun.publish(new Event("loud", UUID.randomUUID(), "loud-" + index));
            }
            quietRun.publish(new Event("quiet", UUID.randomUUID(), Long.toString(System.nanoTime())));

            // When
            releaseDispatcher.countDown();

            // Then
            assertThat(quietReaction.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(loudReactionsAtQuietDispatch.get())
                    .isLessThanOrEqualTo(EventManager.MAX_EVENTS_PER_DISPATCH_TASK);
            assertThat(quietQueueLatencyNanos.get()).isPositive().isLessThan(TimeUnit.SECONDS.toNanos(2));
            assertThat(allReactions.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(loudReactions.get()).isEqualTo(loudEventCount);
            assertThat(loudRun.snapshotStats().droppedEvents()).isZero();
            assertThat(quietRun.snapshotStats().droppedEvents()).isZero();
        } finally {
            releaseDispatcher.countDown();
            loudRun.shutdown();
            quietRun.shutdown();
            reactionExecutor.shutdownNow();
        }
    }

    private static EventManager manager(EventDispatcher dispatcher,
                                        ExecutorService reactionExecutor,
                                        EventReaction<Event> reaction,
                                        int eventQueueCapacity) {
        EventHandlingDefinition definition = EventHandlingDefinition.builder()
                .on(Event.class, reaction)
                .runtimeConfiguration(EventHandlingDefinition.RuntimeConfiguration.builder()
                        .sharedReactionExecutor(reactionExecutor)
                        .eventQueueCapacity(eventQueueCapacity)
                        .shutdownTimeout(Duration.ofSeconds(3))
                        .build())
                .build();
        return new EventManager(definition, new ExecutionContextRegistry(), dispatcher);
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        boolean interrupted = false;
        while (true) {
            try {
                latch.await();
                break;
            } catch (InterruptedException ignored) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
