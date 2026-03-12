package io.github.gear4jtest.core.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import io.github.gear4jtest.core.model.SimpleEventBus;
import org.junit.jupiter.api.Test;

class SimpleEventBusThreadingTest {

    static class CollectListener implements EventListener<Event> {
        final BlockingQueue<Event> queue = new LinkedBlockingQueue<>();
        @Override public void handleEvent(Event e) {
            queue.add(e);
        }
        Event await(long ms) throws InterruptedException {
            return queue.poll(ms, TimeUnit.MILLISECONDS);
        }
    }

    @Test
    void eventBus_shouldRunInSeparateThread_acceptEvents_filter_andStop() throws Exception {
        CollectListener listener = new CollectListener();
        EventBusFilter onlyFoo = e -> e.getName().equals("FOO");

        SimpleEventBus bus = new SimpleEventBus("bus", List.of(onlyFoo), List.of(listener));

        Thread t = new Thread(bus::run);
        t.start();

        var executionId = UUID.randomUUID();
        bus.acceptEvent(new Event("p", executionId, "BAR")); // filtered out
        bus.acceptEvent(new Event("p", executionId, "FOO"));

        Event received = listener.await(2000);
        assertThat(received).isNotNull();
        assertThat(received.getName()).isEqualTo("FOO");

        bus.stopBus();
        t.join(2000);

        assertThat(t.isAlive()).isFalse();
    }
}
