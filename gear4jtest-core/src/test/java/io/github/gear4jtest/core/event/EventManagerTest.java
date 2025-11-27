package io.github.gear4jtest.core.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.gear4jtest.core.model.refactor.EventBus;

@ExtendWith(MockitoExtension.class)
class EventManagerTest {

    @Mock
    private EventBus eventBus1;

    @Mock
    private EventBus eventBus2;

    @Test
    void publish_shouldSendEventToAllBusses() {
        EventManager manager = new EventManager(List.of(eventBus1, eventBus2));
        Event event = new Event("pipe", "exec", "TYPE");

        manager.publish(event);

        verify(eventBus1).acceptEvent(event);
        verify(eventBus2).acceptEvent(event);
    }

    @Test
    void publish_shouldDoNothingIfNoBusConfigured() {
        EventManager manager = new EventManager(List.of());
        Event event = new Event("pipe", "exec", "TYPE");

        manager.publish(event);

        // aucun bus => aucune interaction possible
        // (le test est surtout là pour vérifier qu'il n'y a pas d'exception)
        assertThat(true).isTrue();
    }

    /**
     * Test plus “réel” sur la méthode shutdown :
     * - on démarre un EventBus qui bloque dans run()
     * - shutdown() doit appeler stopBus() et débloquer le thread proprement.
     */
    @Test
    void shutdown_shouldCallStopBusOnAliveThreads() throws Exception {
        BlockingTestEventBus bus = new BlockingTestEventBus();

        EventManager manager = new EventManager(List.of(bus));

        // On attend que run() ait effectivement démarré et bloque
        boolean started = bus.awaitStarted(2, TimeUnit.SECONDS);
        assertThat(started).isTrue();

        manager.shutdown();

        assertThat(bus.isStopCalled()).isTrue();
        // le thread associé doit finalement se terminer (sinon le test bloque)
        boolean finished = bus.awaitFinished(2, TimeUnit.SECONDS);
        assertThat(finished).isTrue();
    }

    /**
     * Implémentation de test d'un EventBus qui :
     * - signale quand run() commence
     * - se bloque jusqu'à l'appel de stopBus()
     * - signale quand run() se termine
     */
    private static class BlockingTestEventBus implements EventBus {

        private final CountDownLatch startedLatch = new CountDownLatch(1);
        private final CountDownLatch stopSignal = new CountDownLatch(1);
        private final CountDownLatch finishedLatch = new CountDownLatch(1);
        private volatile boolean stopCalled = false;

        @Override
        public void run() {
            startedLatch.countDown();
            try {
                // attend le stop
                stopSignal.await();
            } catch (InterruptedException e) {
                // on laisse sortir
                Thread.currentThread().interrupt();
            } finally {
                finishedLatch.countDown();
            }
        }

        @Override
        public void stopBus() {
            stopCalled = true;
            stopSignal.countDown();
        }

        @Override
        public void acceptEvent(Event event) {
            // pas nécessaire pour ce test
        }

        boolean isStopCalled() {
            return stopCalled;
        }

        boolean awaitStarted(long timeout, TimeUnit unit) throws InterruptedException {
            return startedLatch.await(timeout, unit);
        }

        boolean awaitFinished(long timeout, TimeUnit unit) throws InterruptedException {
            return finishedLatch.await(timeout, unit);
        }
    }
}
