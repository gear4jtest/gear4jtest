package io.github.gear4jtest.core.engine;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.gear4jtest.core.util.MonotonicDeadline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class DetachedEventRuntimeCleanupScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(DetachedEventRuntimeCleanupScheduler.class);

    void schedule(Runnable cleanup, CompletableFuture<Void> completion, Duration detachCleanupTimeout) {
        AtomicBoolean cleanupDone = new AtomicBoolean(false);
        Runnable cleanupOnce = () -> {
            if (cleanupDone.compareAndSet(false, true)) {
                cleanup.run();
            }
        };

        completion.whenComplete((ignored, error) -> cleanupOnce.run());

        if (detachCleanupTimeout == null || detachCleanupTimeout.isNegative() || detachCleanupTimeout.isZero()) {
            return;
        }

        CompletableFuture
                .delayedExecutor(MonotonicDeadline.toNanosSaturated(detachCleanupTimeout), TimeUnit.NANOSECONDS)
                .execute(() -> {
                    if (cleanupDone.compareAndSet(false, true)) {
                        LOGGER.warn("Forcing detached event runtime cleanup after timeout. timeout={}",
                                    detachCleanupTimeout);
                        cleanup.run();
                    }
                });
    }
}
