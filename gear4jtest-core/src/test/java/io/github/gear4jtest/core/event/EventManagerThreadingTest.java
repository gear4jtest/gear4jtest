package io.github.gear4jtest.core.event;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.github.gear4jtest.core.api.config.EventHandlingDefinition;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EventManagerThreadingTest {
    @Test
    void reactions_shouldRunOffThePublishingThread() throws Exception {
        AtomicReference<String> threadName = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        EventHandlingDefinition definition = EventHandlingDefinition.builder().on(Event.class, event -> {
            threadName.set(Thread.currentThread().getName());
            latch.countDown();
        }).runtimeConfiguration(EventHandlingDefinition.RuntimeConfiguration.builder()
                .reactionExecutorFactory(Executors::newSingleThreadExecutor).shutdownTimeout(Duration.ofSeconds(2))
                .build()).build();

        EventManager manager = new EventManager(definition, new ExecutionContextRegistry());
        try {
            String publishingThread = Thread.currentThread().getName();
            manager.publish(new Event("pipe", java.util.UUID.randomUUID(), "FOO"));

            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(threadName.get()).isNotBlank();
            assertThat(threadName.get()).isNotEqualTo(publishingThread);
        } finally {
            manager.shutdown();
        }
    }
}
