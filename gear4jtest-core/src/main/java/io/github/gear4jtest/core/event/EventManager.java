package io.github.gear4jtest.core.event;

import io.github.gear4jtest.core.api.config.EventHandlingDefinition;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import io.github.gear4jtest.core.sidecompute.SideComputeListener;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EventManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(EventManager.class);
    private static final Event STOP_EVENT = new Event("__internal__", null, "STOP_EVENT");
    private static final AtomicInteger DISPATCHER_COUNTER = new AtomicInteger();

    public record ShutdownHandle(boolean detached, CompletableFuture<Void> completion) {
        public static ShutdownHandle completed() {
            return new ShutdownHandle(false, CompletableFuture.completedFuture(null));
        }
    }

    private final List<EventSubscription<?>> subscriptions;
    private final BlockingQueue<Event> queue = new LinkedBlockingQueue<>();
    private final Object submissionMonitor = new Object();
    private final AtomicBoolean dispatcherStopped = new AtomicBoolean(false);
    private final AtomicInteger inFlightReactions = new AtomicInteger();
    private final CompletableFuture<Void> terminationFuture = new CompletableFuture<>();

    private final ExecutorService reactionExecutor;
    private final boolean shutdownExecutorOnClose;
    private final Thread dispatcherThread;
    private final Duration shutdownTimeout;
    private final EventHandlingDefinition.RuntimeConfiguration.ShutdownMode shutdownMode;

    private boolean accepting;

    public EventManager(EventHandlingDefinition definition, ExecutionContextRegistry registry) {
        EventHandlingDefinition effectiveDefinition = definition != null ? definition : EventHandlingDefinition.builder().build();

        this.subscriptions = buildSubscriptions(effectiveDefinition, registry);
        this.shutdownTimeout = effectiveDefinition.getRuntimeConfiguration().getShutdownTimeout();
        this.shutdownMode = effectiveDefinition.getRuntimeConfiguration().getShutdownMode();

        if (subscriptions.isEmpty()) {
            this.reactionExecutor = null;
            this.shutdownExecutorOnClose = false;
            this.dispatcherThread = null;
            this.accepting = false;
            this.terminationFuture.complete(null);
            return;
        }

        EventHandlingDefinition.RuntimeConfiguration.ExecutorHandle executorHandle =
                effectiveDefinition.getRuntimeConfiguration().acquireReactionExecutor();
        this.reactionExecutor = executorHandle.executorService();
        this.shutdownExecutorOnClose = executorHandle.shutdownOnClose();
        this.accepting = true;
        this.dispatcherThread = new Thread(
                this::dispatchLoop, "gear4j-event-dispatcher-" + DISPATCHER_COUNTER.incrementAndGet());
        this.dispatcherThread.setDaemon(true);
        this.dispatcherThread.start();
    }

    private static List<EventSubscription<?>> buildSubscriptions(
            EventHandlingDefinition definition, ExecutionContextRegistry registry) {
        List<EventSubscription<?>> resolvedSubscriptions = new ArrayList<>(definition.getSubscriptions());
        resolvedSubscriptions.addAll(SideComputeListener.subscriptions(definition.getSideComputers(), registry));
        return List.copyOf(resolvedSubscriptions);
    }

    public <T extends Event> void publish(T event) {
        Objects.requireNonNull(event, "event");
        if (dispatcherThread == null) {
            return;
        }
        synchronized (submissionMonitor) {
            if (!accepting) {
                return;
            }
            queue.offer(event);
        }
    }

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
                queue.offer(STOP_EVENT);
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

    private void dispatchLoop() {
        try {
            while (true) {
                Event event = queue.take();
                if (event == STOP_EVENT) {
                    break;
                }
                dispatch(event);
            }

            Event remaining;
            while ((remaining = queue.poll()) != null) {
                if (remaining != STOP_EVENT) {
                    dispatch(remaining);
                }
            }
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        } catch (Throwable throwable) {
            terminationFuture.completeExceptionally(throwable);
            return;
        } finally {
            dispatcherStopped.set(true);
            if (shutdownExecutorOnClose && reactionExecutor != null) {
                if (shutdownMode == EventHandlingDefinition.RuntimeConfiguration.ShutdownMode.CANCEL_PENDING_TASKS) {
                    reactionExecutor.shutdownNow();
                } else {
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
            inFlightReactions.incrementAndGet();
            try {
                reactionExecutor.submit(() -> invokeSafely(subscription, event));
            } catch (RejectedExecutionException rejectedExecutionException) {
                inFlightReactions.decrementAndGet();
                LOGGER.warn(
                        "Dropping event reaction because the reaction executor is shutting down. eventType={}, subscriptionType={}",
                        event.getName(),
                        subscription.eventType().getName(),
                        rejectedExecutionException);
                tryCompleteTermination();
            }
        }
    }

    private void invokeSafely(EventSubscription<?> subscription, Event event) {
        try {
            subscription.handle(event);
        } catch (Error error) {
            throw error;
        } catch (Exception exception) {
            LOGGER.error(
                    "Asynchronous event reaction failed. eventType={}, subscriptionType={}",
                    event.getName(),
                    subscription.eventType().getName(),
                    exception);
        } finally {
            inFlightReactions.decrementAndGet();
            tryCompleteTermination();
        }
    }

    private void tryCompleteTermination() {
        if (dispatcherStopped.get() && inFlightReactions.get() == 0) {
            terminationFuture.complete(null);
        }
    }

    private void awaitCompletion(CompletableFuture<Void> completion) {
        try {
            completion.join();
            if (shutdownExecutorOnClose && reactionExecutor != null) {
                boolean terminated = reactionExecutor.awaitTermination(shutdownTimeout.toMillis(), TimeUnit.MILLISECONDS);
                if (!terminated) {
                    LOGGER.warn(
                            "Timed out while waiting for the per-run event reaction executor to terminate. timeout={}",
                            shutdownTimeout);
                    reactionExecutor.shutdownNow();
                    reactionExecutor.awaitTermination(shutdownTimeout.toMillis(), TimeUnit.MILLISECONDS);
                }
            }
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            if (shutdownExecutorOnClose && reactionExecutor != null) {
                reactionExecutor.shutdownNow();
            }
        } catch (RuntimeException runtimeException) {
            LOGGER.warn("Asynchronous event runtime terminated with an error.", runtimeException);
            if (shutdownExecutorOnClose && reactionExecutor != null) {
                reactionExecutor.shutdownNow();
            }
        }
    }
}
