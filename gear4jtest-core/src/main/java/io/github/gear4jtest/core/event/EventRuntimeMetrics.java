package io.github.gear4jtest.core.event;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAccumulator;
import java.util.concurrent.atomic.LongAdder;

import io.github.gear4jtest.core.api.annotation.PublicApi;

/** Tag-free process aggregation for event saturation and dispatch latency. */
@PublicApi
public final class EventRuntimeMetrics {
    private static final AtomicInteger ACTIVE_RUNTIMES = new AtomicInteger();
    private static final AtomicInteger QUEUED_EVENTS = new AtomicInteger();
    private static final AtomicInteger IN_FLIGHT_REACTIONS = new AtomicInteger();
    private static final LongAdder PUBLISHED_EVENTS = new LongAdder();
    private static final LongAdder DISPATCHED_EVENTS = new LongAdder();
    private static final LongAdder SUBMITTED_REACTIONS = new LongAdder();
    private static final LongAdder COMPLETED_REACTIONS = new LongAdder();
    private static final LongAdder DROPPED_EVENTS = new LongAdder();
    private static final LongAdder DROPPED_REACTIONS = new LongAdder();
    private static final LongAdder FAILED_REACTIONS = new LongAdder();
    private static final LongAdder DISPATCHER_REJECTED_TASKS = new LongAdder();
    private static final LongAdder DISPATCH_LATENCY_SAMPLES = new LongAdder();
    private static final LongAdder TOTAL_DISPATCH_LATENCY_NANOS = new LongAdder();
    private static final LongAccumulator MAX_DISPATCH_LATENCY_NANOS = new LongAccumulator(Long::max, 0L);

    private EventRuntimeMetrics() {
    }

    public static ProcessEventRuntimeStats snapshot() {
        return new ProcessEventRuntimeStats(ACTIVE_RUNTIMES.get(), QUEUED_EVENTS.get(), IN_FLIGHT_REACTIONS.get(),
                PUBLISHED_EVENTS.sum(), DISPATCHED_EVENTS.sum(), SUBMITTED_REACTIONS.sum(),
                COMPLETED_REACTIONS.sum(), DROPPED_EVENTS.sum(), DROPPED_REACTIONS.sum(), FAILED_REACTIONS.sum(),
                DISPATCHER_REJECTED_TASKS.sum(), DISPATCH_LATENCY_SAMPLES.sum(),
                TOTAL_DISPATCH_LATENCY_NANOS.sum(), MAX_DISPATCH_LATENCY_NANOS.get());
    }

    static void runtimeOpened() {
        ACTIVE_RUNTIMES.incrementAndGet();
    }

    static void runtimeClosed() {
        ACTIVE_RUNTIMES.decrementAndGet();
    }

    static void eventPublished() {
        PUBLISHED_EVENTS.increment();
        QUEUED_EVENTS.incrementAndGet();
    }

    static void eventDispatched(long queuedNanos) {
        DISPATCHED_EVENTS.increment();
        QUEUED_EVENTS.decrementAndGet();
        long latency = Math.max(0L, System.nanoTime() - queuedNanos);
        DISPATCH_LATENCY_SAMPLES.increment();
        TOTAL_DISPATCH_LATENCY_NANOS.add(latency);
        MAX_DISPATCH_LATENCY_NANOS.accumulate(latency);
    }

    static void eventDroppedFromQueue() {
        DROPPED_EVENTS.increment();
        QUEUED_EVENTS.decrementAndGet();
    }

    static void eventRejectedBeforeQueue() {
        DROPPED_EVENTS.increment();
    }

    static void reactionSubmitted() {
        SUBMITTED_REACTIONS.increment();
    }

    static void reactionStarted() {
        IN_FLIGHT_REACTIONS.incrementAndGet();
    }

    static void reactionCompleted() {
        COMPLETED_REACTIONS.increment();
        IN_FLIGHT_REACTIONS.decrementAndGet();
    }

    static void reactionDropped() {
        DROPPED_REACTIONS.increment();
    }

    static void reactionFailed() {
        FAILED_REACTIONS.increment();
    }

    static void dispatcherTaskRejected() {
        DISPATCHER_REJECTED_TASKS.increment();
    }
}
