package io.github.gear4jtest.studio.runtime;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.github.gear4jtest.studio.model.RunObservation;

/**
 * Host-owned, bounded local progress sampler. One worker, no queue per event.
 * Saturation drops intermediate observations only; final writes remain
 * explicit. A slow sink can delay other intermediate snapshots, never a station
 * callback.
 */
public final class RunObservationDispatcher implements AutoCloseable {
    private final Map<UUID, Runnable> registrations = new ConcurrentHashMap<>();
    private final ScheduledExecutorService worker;
    private final int capacity;
    private boolean closed;

    public RunObservationDispatcher() {
        this(128, Duration.ofMillis(100));
    }

    public RunObservationDispatcher(int capacity, Duration interval) {
        if (capacity < 1 || capacity > 10000 || interval == null || interval.toMillis() < 10
                || interval.compareTo(Duration.ofMinutes(1)) > 0)
            throw new IllegalArgumentException("Invalid observation sampling limits");
        this.capacity = capacity;
        worker = Executors.newSingleThreadScheduledExecutor(task -> {
            var thread = new Thread(task, "studio-observation-progress");
            thread.setDaemon(true);
            return thread;
        });
        worker.scheduleWithFixedDelay(this::sample, interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    synchronized Registration register(Supplier<RunObservation> snapshot, Consumer<RunObservation> sink) {
        if (closed || registrations.size() >= capacity)
            return () -> {
            };
        var id = UUID.randomUUID();
        registrations.put(id, () -> {
            var value = snapshot.get();
            if (value != null)
                sink.accept(value);
        });
        return () -> registrations.remove(id);
    }

    private void sample() {
        for (var registration : registrations.values()) {
            if (Thread.currentThread().isInterrupted())
                return;
            try {
                registration.run();
            } catch (RuntimeException unavailable) {
                /* Best effort; a later dirty snapshot may recover. */ }
        }
    }

    @Override
    public synchronized void close() {
        closed = true;
        registrations.clear();
        worker.shutdownNow();
    }

    interface Registration {
        void close();
    }
}
