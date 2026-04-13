package io.github.gear4jtest.core.event;

import io.github.gear4jtest.core.api.config.EventHandlingDefinition;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import io.github.gear4jtest.core.sidecompute.SideComputeListener;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(EventManager.class);
    private static final Event STOP_EVENT = new Event("__internal__", null, "STOP_EVENT");

    private final List<EventSubscription<?>> subscriptions;
    private final BlockingQueue<Event> queue = new LinkedBlockingQueue<>();
    private final AtomicBoolean accepting = new AtomicBoolean(true);
    private final ExecutorService reactionExecutor;
    private final Thread dispatcherThread;
    private final Duration shutdownTimeout;
    private final EventHandlingDefinition.RuntimeConfiguration.ShutdownMode shutdownMode;

    public EventManager(EventHandlingDefinition definition, ExecutionContextRegistry registry) {
        EventHandlingDefinition effectiveDefinition = definition != null
                ? definition
                : EventHandlingDefinition.builder().build();

        this.subscriptions = buildSubscriptions(effectiveDefinition, registry);
        this.shutdownTimeout = effectiveDefinition.getRuntimeConfiguration().getShutdownTimeout();
        this.shutdownMode = effectiveDefinition.getRuntimeConfiguration().getShutdownMode();

        if (subscriptions.isEmpty()) {
            this.reactionExecutor = null;
            this.dispatcherThread = null;
            this.accepting.set(false);
            return;
        }

        this.reactionExecutor = effectiveDefinition.getRuntimeConfiguration().createReactionExecutor();
        this.dispatcherThread = new Thread(this::dispatchLoop, "gear4j-event-dispatcher");
        this.dispatcherThread.start();
    }

    private static List<EventSubscription<?>> buildSubscriptions(
            EventHandlingDefinition definition,
            ExecutionContextRegistry registry) {
        List<EventSubscription<?>> resolvedSubscriptions = new ArrayList<>(definition.getSubscriptions());
        resolvedSubscriptions.addAll(
                SideComputeListener.subscriptions(definition.getSideComputers(), registry));
        return List.copyOf(resolvedSubscriptions);
    }

    private void dispatchLoop() {
        try {
            while (accepting.get() || !queue.isEmpty()) {
                Event event = queue.poll(200, TimeUnit.MILLISECONDS);
                if (event == null || event == STOP_EVENT) {
                    continue;
                }
                dispatch(event);
            }
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    private void dispatch(Event event) {
        for (EventSubscription<?> subscription : subscriptions) {
            if (!subscription.accepts(event)) {
                continue;
            }
            try {
                reactionExecutor.submit(() -> invokeSafely(subscription, event));
            } catch (RejectedExecutionException rejectedExecutionException) {
                LOGGER.warn(
                        "Dropping event reaction because the reaction executor is shutting down. eventType={}, subscriptionType={}",
                        event.getName(),
                        subscription.eventType().getName(),
                        rejectedExecutionException);
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
        }
    }

    public void shutdown() {
        if (dispatcherThread == null) {
            return;
        }
        if (!accepting.getAndSet(false)) {
            return;
        }

        queue.offer(STOP_EVENT);

        try {
            dispatcherThread.join(shutdownTimeout.toMillis());
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }

        reactionExecutor.shutdown();
        try {
            if (!reactionExecutor.awaitTermination(shutdownTimeout.toMillis(), TimeUnit.MILLISECONDS)
                    && shutdownMode == EventHandlingDefinition.RuntimeConfiguration.ShutdownMode.CANCEL_PENDING_TASKS) {
                reactionExecutor.shutdownNow();
            }
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            if (shutdownMode == EventHandlingDefinition.RuntimeConfiguration.ShutdownMode.CANCEL_PENDING_TASKS) {
                reactionExecutor.shutdownNow();
            }
        }
    }

    public <T extends Event> void publish(T event) {
        Objects.requireNonNull(event, "event");
        if (dispatcherThread == null || !accepting.get()) {
            return;
        }
        queue.offer(event);
    }
}
