package io.github.gear4jtest.core.event;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.gear4jtest.core.api.config.EventHandlingDefinition;
import io.github.gear4jtest.core.util.MonotonicDeadline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Owns the bounded termination policy for an asynchronous event runtime. */
final class EventRuntimeShutdown {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventRuntimeShutdown.class);

    private final ExecutorService reactionExecutor;
    private final boolean shutdownExecutorOnClose;
    private final Duration timeout;
    private final EventHandlingDefinition.RuntimeConfiguration.ShutdownMode mode;
    private final AtomicBoolean ownedExecutorShutdownInitiated = new AtomicBoolean(false);

    private EventRuntimeShutdown(EventHandlingDefinition.RuntimeConfiguration configuration,
                                 ExecutorService reactionExecutor,
                                 boolean shutdownExecutorOnClose) {
        this.reactionExecutor = reactionExecutor;
        this.shutdownExecutorOnClose = shutdownExecutorOnClose;
        this.timeout = configuration.getShutdownTimeout();
        this.mode = configuration.getShutdownMode();
    }

    static EventRuntimeShutdown inactive(EventHandlingDefinition.RuntimeConfiguration configuration) {
        return new EventRuntimeShutdown(Objects.requireNonNull(configuration, "configuration must not be null"),
                null, false);
    }

    static EventRuntimeShutdown active(EventHandlingDefinition.RuntimeConfiguration configuration,
                                       ExecutorService reactionExecutor,
                                       boolean shutdownExecutorOnClose) {
        return new EventRuntimeShutdown(Objects.requireNonNull(configuration, "configuration must not be null"),
                Objects.requireNonNull(reactionExecutor, "reactionExecutor must not be null"),
                shutdownExecutorOnClose);
    }

    Duration timeout() {
        return timeout;
    }

    boolean cancelsPendingTasks() {
        return mode == EventHandlingDefinition.RuntimeConfiguration.ShutdownMode.CANCEL_PENDING_TASKS;
    }

    boolean detachesAndDrains() {
        return mode == EventHandlingDefinition.RuntimeConfiguration.ShutdownMode.DETACH_AND_DRAIN;
    }

    void initiateOwnedExecutorShutdownAfterDispatchDrain() {
        if (!ownsExecutor() || cancelsPendingTasks()) {
            return;
        }
        if (ownedExecutorShutdownInitiated.compareAndSet(false, true)) {
            reactionExecutor.shutdown();
        }
    }

    void awaitCompletion(CompletableFuture<Void> completion,
                         MonotonicDeadline deadline,
                         Runnable cancelPendingReactions) {
        Objects.requireNonNull(completion, "completion must not be null");
        Objects.requireNonNull(deadline, "deadline must not be null");
        Objects.requireNonNull(cancelPendingReactions, "cancelPendingReactions must not be null");
        try {
            completion.get(deadline.remainingNanos(), TimeUnit.NANOSECONDS);
            awaitOwnedExecutorTermination(deadline, cancelPendingReactions);
        } catch (TimeoutException timeoutException) {
            LOGGER.warn("Timed out while waiting for the asynchronous event runtime to terminate. timeout={}",
                        timeout);
            cancelAndForceShutdown(deadline, cancelPendingReactions);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            cancelAndForceShutdown(deadline, cancelPendingReactions);
        } catch (ExecutionException executionException) {
            LOGGER.warn("Asynchronous event runtime terminated with an error.", executionException.getCause());
            cancelAndForceShutdown(deadline, cancelPendingReactions);
        }
    }

    void forceOwnedExecutor(MonotonicDeadline deadline) {
        Objects.requireNonNull(deadline, "deadline must not be null");
        if (!ownsExecutor()) {
            return;
        }

        ownedExecutorShutdownInitiated.set(true);
        reactionExecutor.shutdownNow();
        try {
            reactionExecutor.awaitTermination(deadline.remainingNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    private void awaitOwnedExecutorTermination(MonotonicDeadline deadline,
                                               Runnable cancelPendingReactions)
            throws InterruptedException {
        if (!ownsExecutor()) {
            return;
        }

        boolean terminated = reactionExecutor.awaitTermination(deadline.remainingNanos(), TimeUnit.NANOSECONDS);
        if (!terminated) {
            LOGGER.warn("Timed out while waiting for the per-run event reaction executor to terminate. timeout={}",
                        timeout);
            cancelPendingReactions.run();
            reactionExecutor.shutdownNow();
            reactionExecutor.awaitTermination(deadline.remainingNanos(), TimeUnit.NANOSECONDS);
        }
    }

    private void cancelAndForceShutdown(MonotonicDeadline deadline, Runnable cancelPendingReactions) {
        cancelPendingReactions.run();
        forceOwnedExecutor(deadline);
    }

    private boolean ownsExecutor() {
        return shutdownExecutorOnClose && reactionExecutor != null;
    }
}
