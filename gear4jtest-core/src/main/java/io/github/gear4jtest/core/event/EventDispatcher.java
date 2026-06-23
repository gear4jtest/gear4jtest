package io.github.gear4jtest.core.event;

import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.gear4jtest.core.api.annotation.Internal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared in-memory dispatcher used by per-run {@link EventManager} instances.
 *
 * <p>
 * The dispatcher owns a small fixed set of daemon threads and executes
 * lightweight dispatch tasks that submit matching reactions to the run's
 * configured reaction executor. It deliberately does not provide durability,
 * replay, ordering across independent runs, or process-shutdown guarantees.
 * </p>
 */
@Internal
final class EventDispatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventDispatcher.class);
    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger();
    private static final EventDispatcher SHARED = new EventDispatcher(defaultDispatcherThreadCount());

    private final BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();

    private EventDispatcher(int dispatcherThreadCount) {
        for (int index = 0; index < dispatcherThreadCount; index++) {
            Thread thread = new Thread(this::dispatchLoop,
                    "gear4j-event-dispatcher-" + THREAD_COUNTER.incrementAndGet());
            thread.setDaemon(true);
            thread.start();
        }
    }

    static EventDispatcher shared() {
        return SHARED;
    }

    boolean submit(Runnable task) {
        return queue.offer(Objects.requireNonNull(task, "task"));
    }

    private void dispatchLoop() {
        while (true) {
            try {
                queue.take().run();
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException runtimeException) {
                LOGGER.error("Shared event dispatcher task failed unexpectedly.", runtimeException);
            }
        }
    }

    private static int defaultDispatcherThreadCount() {
        int processors = Runtime.getRuntime().availableProcessors();
        return Math.max(1, Math.min(processors, 4));
    }
}
