package io.github.gear4jtest.core.event;

import java.time.Duration;
import java.util.List;
import java.util.Map;
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

import io.github.gear4jtest.core.api.annotation.Internal;
import io.github.gear4jtest.core.api.config.EventHandlingDefinition;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Runtime controller for asynchronous pipeline events.
 *
 * <p>
 * This runtime is deliberately <strong>best-effort</strong>. Reactions are
 * delivered through an in-memory shared dispatcher and submitted to an
 * {@link ExecutorService}. There is no durable storage, no transactional
 * hand-off, and no replay mechanism. As a consequence, the runtime does
 * <strong>not</strong> provide guaranteed delivery, exactly-once execution, or
 * recovery after process failure.
 * </p>
 *
 * <p>
 * Each {@code EventManager} still owns run-local subscriptions, counters,
 * shutdown semantics and queue-capacity accounting, but it no longer creates
 * one dedicated dispatcher thread per run. Lightweight dispatch tasks are
 * multiplexed by a shared in-process dispatcher.
 * </p>
 *
 * <p>
 * Reactions may be dropped in particular when the configured executor rejects
 * submissions, for example because it is saturated or shutting down. Dropped
 * reactions are logged and counted in {@link #snapshotStats()}.
 * </p>
 */
@Internal
public final class EventManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventManager.class);
    private final List<EventSubscription<?>> subscriptions;
    private final BlockingQueue<QueuedEvent> queue;
    private final Object submissionMonitor = new Object();
    private final AtomicBoolean shutdownStarted = new AtomicBoolean(false);
    private final AtomicBoolean ownedExecutorShutdownInitiated = new AtomicBoolean(false);
    private final AtomicBoolean dispatchTaskScheduled = new AtomicBoolean(false);
    private final AtomicInteger dispatchingEvents = new AtomicInteger();
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
    private final Duration shutdownTimeout;
    private final EventHandlingDefinition.RuntimeConfiguration.ShutdownMode shutdownMode;
    private final int eventQueueCapacity;
    /** Guarded by {@link #submissionMonitor}. */
    private boolean accepting;

    public EventManager(EventHandlingDefinition definition, ExecutionContextRegistry registry) {
        EventHandlingDefinition effectiveDefinition = definition != null ? definition
                : EventHandlingDefinition.builder().build();

        EventHandlingDefinition.RuntimeConfiguration runtimeConfiguration = effectiveDefinition
                .getRuntimeConfiguration();
        this.subscriptions = EventSubscriptionResolver.resolve(effectiveDefinition, registry);
        this.shutdownTimeout = runtimeConfiguration.getShutdownTimeout();
        this.shutdownMode = runtimeConfiguration.getShutdownMode();
        this.eventQueueCapacity = runtimeConfiguration.getEventQueueCapacity();
        this.queue = new LinkedBlockingQueue<>(eventQueueCapacity);

        if (subscriptions.isEmpty()) {
            this.reactionExecutor = null;
            this.shutdownExecutorOnClose = false;
            this.accepting = false;
            this.terminationFuture.complete(null);
            return;
        }

        EventHandlingDefinition.RuntimeConfiguration.ExecutorHandle executorHandle = runtimeConfiguration
                .acquireReactionExecutor();
        this.reactionExecutor = executorHandle.executorService();
        this.shutdownExecutorOnClose = executorHandle.shutdownOnClose();
        this.accepting = true;
        EventRuntimeMetrics.runtimeOpened();
        this.terminationFuture.whenComplete((ignored, failure) -> EventRuntimeMetrics.runtimeClosed());
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
        if (subscriptions.isEmpty()) {
            return;
        }

        synchronized (submissionMonitor) {
            if (!accepting) {
                return;
            }
            if (queue.offer(QueuedEvent.of(event, MDC.getCopyOfContextMap()))) {
                publishedEvents.incrementAndGet();
                EventRuntimeMetrics.eventPublished();
            } else {
                droppedEvents.incrementAndGet();
                EventRuntimeMetrics.eventRejectedBeforeQueue();
                LOGGER.warn("Dropping event because the in-memory event queue is full. eventType={}, capacity={}",
                            event.getName(), eventQueueCapacity);
                return;
            }
        }

        scheduleDispatchIfNeeded();
    }

    /**
     * Initiates shutdown of the asynchronous event runtime.
     *
     * <p>
     * Depending on the configured shutdown mode, this may wait for already accepted
     * events and reactions to drain, detach and let the drain finish in the
     * background, or cancel pending queued work. Even in drain modes, the runtime
     * remains best-effort: a saturated executor may still reject some reactions.
     * </p>
     */
    public ShutdownHandle shutdown() {
        if (subscriptions.isEmpty()) {
            return ShutdownHandle.completed();
        }

        synchronized (submissionMonitor) {
            if (accepting) {
                accepting = false;
                shutdownStarted.set(true);
                if (shutdownMode == EventHandlingDefinition.RuntimeConfiguration.ShutdownMode.CANCEL_PENDING_TASKS) {
                    dropPendingQueuedEvents();
                    cancelPendingReactions();
                    forceShutdownOwnedExecutor();
                } else {
                    scheduleDispatchIfNeeded();
                }
                tryCompleteTermination();
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

    private void scheduleDispatchIfNeeded() {
        if (!dispatchTaskScheduled.compareAndSet(false, true)) {
            return;
        }
        boolean submitted = EventDispatcher.shared().submit(this::dispatchFromSharedDispatcher);
        if (!submitted) {
            dispatchTaskScheduled.set(false);
            dropPendingQueuedEvents();
            LOGGER.warn("Dropping pending events because the shared event dispatcher rejected the dispatch task.");
            tryCompleteTermination();
        }
    }

    private void dispatchFromSharedDispatcher() {
        dispatchingEvents.incrementAndGet();
        try {
            QueuedEvent queuedEvent;
            while ((queuedEvent = queue.poll()) != null) {
                dispatchedEvents.incrementAndGet();
                EventRuntimeMetrics.eventDispatched(queuedEvent.queuedNanos());
                dispatch(queuedEvent);
            }
        } catch (RuntimeException runtimeException) {
            terminationFuture.completeExceptionally(runtimeException);
            throw runtimeException;
        } finally {
            dispatchingEvents.decrementAndGet();
            dispatchTaskScheduled.set(false);
            if (!queue.isEmpty()) {
                scheduleDispatchIfNeeded();
            }
            tryCompleteTermination();
        }
    }

    private void dropPendingQueuedEvents() {
        QueuedEvent ignored;
        while ((ignored = queue.poll()) != null) {
            droppedEvents.incrementAndGet();
            EventRuntimeMetrics.eventDroppedFromQueue();
        }
    }

    private void dispatch(QueuedEvent queuedEvent) {
        Event event = queuedEvent.event();
        for (EventSubscription<?> subscription : subscriptions) {
            if (!subscription.supports(event)) {
                continue;
            }
            ReactionTask task = new ReactionTask(subscription, event, queuedEvent.mdcContext());
            activeReactions.add(task);
            acceptedReactions.incrementAndGet();
            try {
                reactionExecutor.execute(task);
                submittedReactions.incrementAndGet();
                EventRuntimeMetrics.reactionSubmitted();
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
        } catch (Exception exception) {
            failedReactions.incrementAndGet();
            EventRuntimeMetrics.reactionFailed();
            LOGGER.error("Asynchronous event reaction failed. eventType={}, subscriptionType={}", event.getName(),
                         subscription.eventType().getName(), exception);
        }
    }

    private void evaluateAndInvokeSafely(EventSubscription<?> subscription, Event event) {
        boolean accepted;
        try {
            accepted = subscription.testPredicate(event);
        } catch (RuntimeException exception) {
            failedReactions.incrementAndGet();
            EventRuntimeMetrics.reactionFailed();
            LOGGER.error("Asynchronous event predicate failed. eventType={}, subscriptionType={}", event.getName(),
                         subscription.eventType().getName(), exception);
            return;
        }
        if (accepted) {
            invokeSafely(subscription, event);
        }
    }

    private void markReactionCompleted(ReactionTask task) {
        if (task.markCompleted()) {
            completedReactions.incrementAndGet();
            EventRuntimeMetrics.reactionCompleted();
            inFlightReactions.decrementAndGet();
            acceptedReactions.decrementAndGet();
            activeReactions.remove(task);
            tryCompleteTermination();
        }
    }

    private void markReactionDroppedBeforeExecution(ReactionTask task) {
        if (task.markCancelledBeforeStart()) {
            droppedReactions.incrementAndGet();
            EventRuntimeMetrics.reactionDropped();
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
                queue.size(), queue.remainingCapacity(), acceptedReactions.get(), inFlightReactions.get());
    }

    private void tryCompleteTermination() {
        if (!shutdownStarted.get()) {
            return;
        }
        if (queue.isEmpty() && dispatchingEvents.get() == 0) {
            initiateOwnedExecutorShutdownAfterDispatchDrain();
            if (acceptedReactions.get() == 0) {
                terminationFuture.complete(null);
            }
        }
    }

    private void initiateOwnedExecutorShutdownAfterDispatchDrain() {
        if (!shutdownExecutorOnClose || reactionExecutor == null) {
            return;
        }
        if (shutdownMode == EventHandlingDefinition.RuntimeConfiguration.ShutdownMode.CANCEL_PENDING_TASKS) {
            return;
        }
        if (ownedExecutorShutdownInitiated.compareAndSet(false, true)) {
            reactionExecutor.shutdown();
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

        ownedExecutorShutdownInitiated.set(true);
        reactionExecutor.shutdownNow();
        try {
            reactionExecutor.awaitTermination(shutdownTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    private record QueuedEvent(Event event, Map<String, String> mdcContext, long queuedNanos) {
        private static QueuedEvent of(Event event, Map<String, String> mdcContext) {
            Map<String, String> immutableContext = mdcContext == null ? null : Map.copyOf(mdcContext);
            return new QueuedEvent(event, immutableContext, System.nanoTime());
        }
    }

    private final class ReactionTask implements Runnable {
        private final EventSubscription<?> subscription;
        private final Event event;
        private final Map<String, String> mdcContext;
        private final AtomicReference<ReactionTaskState> state = new AtomicReference<>(ReactionTaskState.NEW);

        private ReactionTask(EventSubscription<?> subscription, Event event, Map<String, String> mdcContext) {
            this.subscription = subscription;
            this.event = event;
            this.mdcContext = mdcContext;
        }

        @Override
        public void run() {
            if (!state.compareAndSet(ReactionTaskState.NEW, ReactionTaskState.RUNNING)) {
                return;
            }
            inFlightReactions.incrementAndGet();
            EventRuntimeMetrics.reactionStarted();
            try (MdcScope ignored = MdcScope.install(mdcContext)) {
                evaluateAndInvokeSafely(subscription, event);
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
