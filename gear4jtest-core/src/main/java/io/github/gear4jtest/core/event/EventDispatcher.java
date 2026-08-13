package io.github.gear4jtest.core.event;

import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

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
    private static final String QUEUE_CAPACITY_PROPERTY = "gear4j.event.dispatcher.queue-capacity";
    private static final int DEFAULT_QUEUE_CAPACITY = 4_096;
    private static final EventDispatcher SHARED = new EventDispatcher(defaultDispatcherThreadCount(),
            defaultQueueCapacity());

    private final BlockingQueue<Runnable> queue;
    private final LongAdder submittedTasks = new LongAdder();
    private final LongAdder completedTasks = new LongAdder();
    private final LongAdder rejectedTasks = new LongAdder();
    private final LongAdder failedTasks = new LongAdder();

    EventDispatcher(int dispatcherThreadCount, int queueCapacity) {
        if (dispatcherThreadCount <= 0) {
            throw new IllegalArgumentException("dispatcherThreadCount must be > 0");
        }
        if (queueCapacity <= 0) {
            throw new IllegalArgumentException("queueCapacity must be > 0");
        }
        this.queue = new ArrayBlockingQueue<>(queueCapacity);
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
        boolean submitted = queue.offer(Objects.requireNonNull(task, "task"));
        if (submitted) {
            submittedTasks.increment();
        } else {
            rejectedTasks.increment();
            EventRuntimeMetrics.dispatcherTaskRejected();
        }
        return submitted;
    }

    EventDispatcherStats snapshotStats() {
        return new EventDispatcherStats(submittedTasks.sum(), completedTasks.sum(), rejectedTasks.sum(),
                failedTasks.sum(), queue.size(), queue.remainingCapacity());
    }

    private void dispatchLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            Runnable task;
            try {
                task = queue.take();
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                return;
            }
            try {
                task.run();
                completedTasks.increment();
            } catch (RuntimeException runtimeException) {
                failedTasks.increment();
                LOGGER.error("Shared event dispatcher task failed unexpectedly.", runtimeException);
            }
        }
    }

    private static int defaultDispatcherThreadCount() {
        int processors = Runtime.getRuntime().availableProcessors();
        return Math.max(1, Math.min(processors, 4));
    }

    private static int defaultQueueCapacity() {
        String configuredValue = System.getProperty(QUEUE_CAPACITY_PROPERTY);
        if (configuredValue == null || configuredValue.isBlank()) {
            return DEFAULT_QUEUE_CAPACITY;
        }
        try {
            int capacity = Integer.parseInt(configuredValue);
            if (capacity > 0) {
                return capacity;
            }
        } catch (NumberFormatException ignored) {
            // Fall through to the safe default and emit one startup warning.
        }
        LOGGER.warn("Ignoring invalid {} value '{}'; using default capacity {}.", QUEUE_CAPACITY_PROPERTY,
                    configuredValue, DEFAULT_QUEUE_CAPACITY);
        return DEFAULT_QUEUE_CAPACITY;
    }

    record EventDispatcherStats(long submittedTasks,
                                long completedTasks,
                                long rejectedTasks,
                                long failedTasks,
                                int queuedTasks,
                                int remainingCapacity) {}
}
