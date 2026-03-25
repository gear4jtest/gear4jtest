package io.github.gear4jtest.core.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.gear4jtest.core.event.SimpleEventBus;
import org.junit.jupiter.api.Test;

import io.github.gear4jtest.core.event.Event;
import io.github.gear4jtest.core.event.EventBusFilter;
import io.github.gear4jtest.core.event.EventListener;

class SimpleEventBusTest {

    static class CountingListener implements EventListener<Event> {
        private final AtomicInteger counter;
        private final CountDownLatch latch;

        CountingListener(AtomicInteger counter, CountDownLatch latch) {
            this.counter = counter;
            this.latch = latch;
        }

        @Override
        public void handleEvent(Event e) {
            counter.incrementAndGet();
            latch.countDown();
        }
    }

    @Test
    void run_shouldDispatchEventsToListenersRespectingFilters_andStopOnStopBus() throws Exception {
        AtomicInteger handled = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);

        EventBusFilter acceptOnlyFoo = e -> "FOO".equals(e.getName());

        CountingListener listener = new CountingListener(handled, latch);

        SimpleEventBus bus = new SimpleEventBus(
                "bus-1",
                List.of(acceptOnlyFoo),
                List.of(listener)
        );

        Thread busThread = new Thread(bus::run);
        busThread.start();

        // Un event filtré (ne doit pas passer)
        bus.acceptEvent(new Event("p", UUID.randomUUID(), "BAR"));
        // Un event accepté (doit passer)
        bus.acceptEvent(new Event("p", UUID.randomUUID(), "FOO"));

        // On attend que le listener ait été appelé au moins une fois
        boolean received = latch.await(2, TimeUnit.SECONDS);
        assertThat(received).isTrue();
        assertThat(handled.get()).isEqualTo(1);

        // Arrêt propre du bus
        bus.stopBus();
        busThread.join(2000);

        // À ce stade, le thread a traité la file et s’est arrêté sans throw
        assertThat(busThread.isAlive()).isFalse();
    }
}
