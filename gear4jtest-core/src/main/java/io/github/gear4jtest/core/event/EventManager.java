package io.github.gear4jtest.core.event;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import io.github.gear4jtest.core.api.config.EventHandlingDefinition;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import io.github.gear4jtest.core.sidecompute.SideComputeListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runtime dispatcher for asynchronous pipeline events.
 *
 * <p>
 * This runtime is deliberately <strong>best-effort</strong>. Reactions are
 * delivered through an in-memory queue and submitted to an
 * {@link ExecutorService}. There is no durable storage, no transactional
 * hand-off, and no replay mechanism. As a consequence, the runtime does
 * <strong>not</strong> provide guaranteed delivery, exactly-once execution, or
 * recovery after process failure.
 * </p>
 *
 * <p>
 * Reactions may be dropped in particular when the configured executor rejects
 * submissions, for example because it is saturated or shutting down. Dropped
 * reactions are logged and counted in {@link #snapshotStats()}.
 * </p>
 */
public final class EventManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventManager.class);
    private static final Event STOP_EVENT = new Event("__internal__", null, "STOP_EVENT");
    private static final AtomicInteger DISPATCHER_COUNTER = new AtomicInteger();
    private final List<EventSubscription<?>> subscriptions;
    private final BlockingQueue<Event> queue;
    private final Object submissionMonitor = new Object();
    private final AtomicBoolean dispatcherStopped = new AtomicBoolean(false);
    private final AtomicInteger acceptedReactions = new AtomicInteger();
    private final AtomicInteger inFlightReactions = new AtomicInteger();
    private final Set<ReactionTask> activeReactions = ConcurrentHashMap.newKeySet();
    private final CompletableFuture<Void> terminationFuture = new CompletableFuture<>();
    private final AtomicLong publishedEvents = new AtomicLong();
    private final AtomicLong dispatchedEvents = new AtomicLong();
    private final AtomicLong droppedEvents = new AtomicLong();
    private final AtomicLong submittedReactions = new AtomicLong();
    private final AtomicLong completedReactions = new AtomicLong();
    private final AtomicLong droppedReactions = new AtomicLong();
    private final AtomicLong failedReactions = new AtomicLong();
    private final ExecutorService reactionExecutor;
    private final boolean shutdownExecutorOnClose;
    private final Thread dispatcherThread;
    private final Duration shutdownTimeout;
    private final EventHandlingDefinition.RuntimeConfiguration.ShutdownMode shutdownMode;
    /** Guarded by {@link #submissionMonitor}. */
    private boolean accepting;

    public EventManager(EventHandlingDefinition definition, ExecutionContextRegistry registry) {
        EventHandlingDefinition effectiveDefinition = definition != null ? definition
                : EventHandlingDefinition.builder().build();

        EventHandlingDefinition.RuntimeConfiguration runtimeConfiguration = effectiveDefinition
                .getRuntimeConfiguration();
        this.subscriptions = buildSubscriptions(effectiveDefinition, registry);
        this.shutdownTimeout = runtimeConfiguration.getShutdownTimeout();
        this.shutdownMode = runtimeConfiguration.getShutdownMode();
        this.queue = new LinkedBlockingQueue<>(runtimeConfiguration.getEventQueueCapacity());

        if (subscriptions.isEmpty()) {
            this.reactionExecutor = null;
            this.shutdownExecutorOnClose = false;
            this.dispatcherThread = null;
            this.accepting = false;
            this.terminationFuture.complete(null);
            return;
        }

        EventHandlingDefinition.RuntimeConfiguration.ExecutorHandle executorHandle = runtimeConfiguration
                .acquireReactionExecutor();
        this.reactionExecutor = executorHandle.executorService();
        this.shutdownExecutorOnClose = executorHandle.shutdownOnClose();
        this.accepting = true;
        this.dispatcherThread = new Thread(this::dispatchLoop,
                "gear4j-event-dispatcher-" + DISPATCHER_COUNTER.incrementAndGet());
        this.dispatcherThread.setDaemon(true);
        this.dispatcherThread.start();
    }

    private static List<EventSubscription<?>> buildSubscriptions(EventHandlingDefinition definition,
                                                                 ExecutionContextRegistry registry) {
        List<EventSubscription<?>> resolvedSubscriptions = new ArrayList<>(definition.getSubscriptions());
        resolvedSubscriptions.addAll(SideComputeListener.subscriptions(definition.getSideComputers(), registry));
        return List.copyOf(resolvedSubscriptions);
    }

    /**
     * Publishes an event to the asynchronous runtime.
     *
     * <p>
     * The event is accepted only while the runtime is still open for submissions.
     * Once shutdown has started, further events are ignored. Accepted events are
     * still processed in a best-effort fashion only; downstream reactions may later
     * be dropped if the reaction executor rejects them.
     * </p>
     */
    public <T extends Event> void publish(T event) {
        Objects.requireNonNull(event, "event");
        if (dispatcherThread == null) {
            return;
        }
        synchronized (submissionMonitor) {
            if (!accepting) {
                return;
            }
            if (queue.offer(event)) {
                publishedEvents.incrementAndGet();
            } else {
                droppedEvents.incrementAndGet();
                LOGGER.warn("Dropping event because the in-memory event queue is full. eventType={}, capacity={}",
                            event.getName(), queue.remainingCapacity() + queue.size());
            }
        }
    }

    /**
     * Initiates shutdown of the asynchronous event runtime.
     *
     * <p>
     * Depending on the configured shutdown mode, this may wait for the queue to
     * drain, detach and let the drain finish in the background, or cancel pending
     * queued work. Even in drain modes, the runtime remains best-effort: a
     * saturated executor may still reject some reactions.
     * </p>
     */
    public ShutdownHandle shutdown() {
        if (dispatcherThread == null) {
            return ShutdownHandle.completed();
        }

        synchronized (submissionMonitor) {
            if (accepting) {
                accepting = false;
                if (shutdownMode == EventHandlingDefinition.RuntimeConfiguration.ShutdownMode.CANCEL_PENDING_TASKS) {
                    queue.clear();
                }
                enqueueStopEvent();
            }
        }

        ShutdownHandle handle = new ShutdownHandle(
                shutdownMode == EventHandlingDefinition.RuntimeConfiguration.ShutdownMode.DETACH_AND_DRAIN,
                terminationFuture);

        if (!handle.detached()) {
            awaitCompletion(handle.completion());
        }

        return handle;
    }

    private void enqueueStopEvent() {
        try {
            if (!queue.offer(STOP_EVENT, shutdownTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                queue.clear();
                queue.offer(STOP_EVENT);
                LOGGER.warn("Timed out while enqueueing the event-runtime stop signal. Pending events were discarded. "
                        + "timeout={}", shutdownTimeout);
            }
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            queue.clear();
            queue.offer(STOP_EVENT);
        }
    }

    private void dispatchLoop() {
        try {
            while (true) {
                Event event = queue.take();
                if (event == STOP_EVENT) {
                    break;
                }
                dispatchedEvents.incrementAndGet();
                dispatch(event);
            }

            Event remaining;
            while ((remaining = queue.poll()) != null) {
                if (remaining != STOP_EVENT) {
                    dispatchedEvents.incrementAndGet();
                    dispatch(remaining);
                }
            }
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        } catch (Throwable throwable) {
            terminationFuture.completeExceptionally(throwable);
        } finally {
            dispatcherStopped.set(true);
            if (reactionExecutor != null) {
                if (shutdownMode == EventHandlingDefinition.RuntimeConfiguration.ShutdownMode.CANCEL_PENDING_TASKS) {
                    if (shutdownExecutorOnClose) {
                        reactionExecutor.shutdownNow();
                    }
                    cancelPendingReactions();
                } else if (shutdownExecutorOnClose) {
                    reactionExecutor.shutdown();
                }
            }
            tryCompleteTermination();
        }
    }

    private void dispatch(Event event) {
        for (EventSubscription<?> subscription : subscriptions) {
            if (!subscription.accepts(event)) {
                continue;
            }
            ReactionTask task = new ReactionTask(subscription, event);
            activeReactions.add(task);
            acceptedReactions.incrementAndGet();
            try {
                reactionExecutor.execute(task);
                submittedReactions.incrementAndGet();
            } catch (RejectedExecutionException rejectedExecutionException) {
                markReactionDroppedBeforeExecution(task);
                LOGGER.warn("Dropping event reaction because the reaction executor rejected the submission. "
                        + "eventType={}, subscriptionType={}",
                            event.getName(), subscription.eventType().getName(), rejectedExecutionException);
            } catch (RuntimeException runtimeException) {
                markReactionDroppedBeforeExecution(task);
                LOGGER.error("Dropping event reaction because submitting it to the reaction executor failed unexpectedly. "
                        + "eventType={}, subscriptionType={}",
                             event.getName(), subscription.eventType().getName(), runtimeException);
            }
        }
    }

    private void invokeSafely(EventSubscription<?> subscription, Event event) {
        try {
            subscription.handle(event);
        } catch (Error error) {
            throw error;
        } catch (Exception exception) {
            failedReactions.incrementAndGet();
            LOGGER.error("Asynchronous event reaction failed. eventType={}, subscriptionType={}", event.getName(),
                         subscription.eventType().getName(), exception);
        }
    }

    private void markReactionCompleted(ReactionTask task) {
        if (task.markCompleted()) {
            completedReactions.incrementAndGet();
            inFlightReactions.decrementAndGet();
            acceptedReactions.decrementAndGet();
            activeReactions.remove(task);
            tryCompleteTermination();
        }
    }

    private void markReactionDroppedBeforeExecution(ReactionTask task) {
        if (task.markCancelledBeforeStart()) {
            droppedReactions.incrementAndGet();
            acceptedReactions.decrementAndGet();
            activeReactions.remove(task);
            tryCompleteTermination();
        }
    }

    private void cancelPendingReactions() {
        for (ReactionTask task : activeReactions) {
            markReactionDroppedBeforeExecution(task);
        }
    }

    /**
     * Returns a point-in-time snapshot of the asynchronous runtime counters.
     *
     * <p>
     * This is primarily intended for observability. In particular,
     * {@code droppedEvents} and {@code droppedReactions} let callers detect queue
     * or executor saturation without relying solely on log inspection.
     * </p>
     */
    public EventRuntimeStats snapshotStats() {
        return new EventRuntimeStats(publishedEvents.get(), dispatchedEvents.get(), submittedReactions.get(),
                completedReactions.get(), droppedReactions.get(), failedReactions.get(), droppedEvents.get(),
                queue.size());
    }

    private void tryCompleteTermination() {
        if (dispatcherStopped.get() && acceptedReactions.get() == 0) {
            terminationFuture.complete(null);
        }
    }

    private void awaitCompletion(CompletableFuture<Void> completion) {
        try {
            completion.get(shutdownTimeout.toMillis(), TimeUnit.MILLISECONDS);
            awaitOwnedExecutorTermination();
        } catch (TimeoutException timeoutException) {
            LOGGER.warn("Timed out while waiting for the asynchronous event runtime to terminate. timeout={}",
                        shutdownTimeout);
            cancelPendingReactions();
            forceShutdownOwnedExecutor();
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            cancelPendingReactions();
            forceShutdownOwnedExecutor();
        } catch (ExecutionException executionException) {
            LOGGER.warn("Asynchronous event runtime terminated with an error.", executionException.getCause());
            cancelPendingReactions();
            forceShutdownOwnedExecutor();
        }
    }

    private void awaitOwnedExecutorTermination() throws InterruptedException {
        if (!shutdownExecutorOnClose || reactionExecutor == null) {
            return;
        }

        boolean terminated = reactionExecutor.awaitTermination(shutdownTimeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!terminated) {
            LOGGER.warn("Timed out while waiting for the per-run event reaction executor to terminate. timeout={}",
                        shutdownTimeout);
            cancelPendingReactions();
            reactionExecutor.shutdownNow();
            reactionExecutor.awaitTermination(shutdownTimeout.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    private void forceShutdownOwnedExecutor() {
        if (!shutdownExecutorOnClose || reactionExecutor == null) {
            return;
        }

        reactionExecutor.shutdownNow();
        try {
            reactionExecutor.awaitTermination(shutdownTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    private final class ReactionTask implements Runnable {
        private final EventSubscription<?> subscription;
        private final Event event;
        private final AtomicReference<ReactionTaskState> state = new AtomicReference<>(ReactionTaskState.NEW);

        private ReactionTask(EventSubscription<?> subscription, Event event) {
            this.subscription = subscription;
            this.event = event;
        }

        @Override
        public void run() {
            if (!state.compareAndSet(ReactionTaskState.NEW, ReactionTaskState.RUNNING)) {
                return;
            }
            inFlightReactions.incrementAndGet();
            try {
                invokeSafely(subscription, event);
            } finally {
                markReactionCompleted(this);
            }
        }

        private boolean markCompleted() {
            return state.compareAndSet(ReactionTaskState.RUNNING, ReactionTaskState.FINISHED);
        }

        private boolean markCancelledBeforeStart() {
            return state.compareAndSet(ReactionTaskState.NEW, ReactionTaskState.CANCELLED);
        }
    }

    private enum ReactionTaskState {
        NEW, RUNNING, FINISHED, CANCELLED
    }

    public record ShutdownHandle(boolean detached, CompletableFuture<Void> completion) {
        public static ShutdownHandle completed() {
            return new ShutdownHandle(false, CompletableFuture.completedFuture(null));
        }
    }
}
