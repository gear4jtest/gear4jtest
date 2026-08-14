package io.github.gear4jtest.core.engine;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.github.gear4jtest.core.util.MonotonicDeadline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class DetachedEventRuntimeCleanupScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(DetachedEventRuntimeCleanupScheduler.class);
    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger();
    private static final ScheduledThreadPoolExecutor DEFAULT_SCHEDULER = createDefaultScheduler();

    private final ScheduledThreadPoolExecutor scheduler;

    DetachedEventRuntimeCleanupScheduler() {
        this(DEFAULT_SCHEDULER);
    }

    DetachedEventRuntimeCleanupScheduler(ScheduledThreadPoolExecutor scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler must not be null");
    }

    void schedule(Runnable cleanup, CompletableFuture<Void> completion, Duration detachCleanupTimeout) {
        Objects.requireNonNull(cleanup, "cleanup must not be null");
        Objects.requireNonNull(completion, "completion must not be null");
        AtomicReference<Runnable> pendingCleanup = new AtomicReference<>(cleanup);
        AtomicReference<ScheduledFuture<?>> timeoutTask = new AtomicReference<>();

        completion.whenComplete((ignored, error) -> {
            try {
                runCleanupOnce(pendingCleanup);
            } finally {
                ScheduledFuture<?> scheduled = timeoutTask.get();
                if (scheduled != null) {
                    scheduled.cancel(false);
                }
            }
        });

        if (detachCleanupTimeout == null || detachCleanupTimeout.isNegative() || detachCleanupTimeout.isZero()) {
            return;
        }

        try {
            ScheduledFuture<?> scheduled = scheduler.schedule(() -> {
                Runnable claimedCleanup = pendingCleanup.getAndSet(null);
                if (claimedCleanup != null) {
                    LOGGER.warn("Forcing detached event runtime cleanup after timeout. timeout={}",
                                detachCleanupTimeout);
                    runCleanup(claimedCleanup);
                }
            }, MonotonicDeadline.toNanosSaturated(detachCleanupTimeout), TimeUnit.NANOSECONDS);
            timeoutTask.set(scheduled);
            if (pendingCleanup.get() == null) {
                scheduled.cancel(false);
            }
        } catch (RejectedExecutionException rejected) {
            LOGGER.warn("Detached event cleanup timeout scheduling was rejected; cleaning up immediately. timeout={}",
                        detachCleanupTimeout, rejected);
            runCleanupOnce(pendingCleanup);
        }
    }

    private static ScheduledThreadPoolExecutor createDefaultScheduler() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, task -> {
            Thread thread = new Thread(task, "gear4j-detached-cleanup-" + THREAD_COUNTER.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
        executor.setRemoveOnCancelPolicy(true);
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        return executor;
    }

    private static void runCleanupOnce(AtomicReference<Runnable> pendingCleanup) {
        Runnable cleanup = pendingCleanup.getAndSet(null);
        if (cleanup != null) {
            runCleanup(cleanup);
        }
    }

    private static void runCleanup(Runnable cleanup) {
        try {
            cleanup.run();
        } catch (RuntimeException failure) {
            LOGGER.warn("Detached event runtime cleanup failed.", failure);
        }
    }
}
