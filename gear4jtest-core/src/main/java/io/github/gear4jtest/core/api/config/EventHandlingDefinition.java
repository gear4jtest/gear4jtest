package io.github.gear4jtest.core.api.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import io.github.gear4jtest.core.event.Event;
import io.github.gear4jtest.core.event.EventPayloadPolicy;
import io.github.gear4jtest.core.event.EventReaction;
import io.github.gear4jtest.core.event.EventSubscription;
import io.github.gear4jtest.core.sidecompute.SideComputer;

/**
 * Declarative configuration for the asynchronous event runtime attached to a
 * pipeline.
 *
 * <p>
 * The event runtime is intentionally <strong>best-effort</strong>. Events are
 * kept in memory and reactions are submitted to an executor. The runtime does
 * not provide durable persistence, transactional hand-off, replay, or
 * exactly-once guarantees. Callers that require guaranteed delivery must route
 * events to a durable external system instead of relying solely on the
 * in-process runtime.
 * </p>
 *
 * <p>
 * When the reaction executor is saturated or shutting down, some reactions may
 * be rejected and dropped. Those drops are logged and exposed through
 * {@code EventManager.snapshotStats()} for observability.
 * </p>
 */
public class EventHandlingDefinition {
    private final List<EventSubscription<?>> subscriptions;
    private final List<SideComputer<?, ?, ?>> sideComputers;
    private final EventConfiguration globalEventConfiguration;
    private final RuntimeConfiguration runtimeConfiguration;

    private EventHandlingDefinition(List<EventSubscription<?>> subscriptions,
                                    List<SideComputer<?, ?, ?>> sideComputers,
                                    EventConfiguration globalEventConfiguration,
                                    RuntimeConfiguration runtimeConfiguration) {
        this.subscriptions = subscriptions != null ? List.copyOf(subscriptions) : List.of();
        this.sideComputers = sideComputers != null ? List.copyOf(sideComputers) : List.of();
        this.globalEventConfiguration = globalEventConfiguration != null ? globalEventConfiguration
                : EventConfiguration.builder().build();
        this.runtimeConfiguration = runtimeConfiguration != null ? runtimeConfiguration
                : RuntimeConfiguration.builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<EventSubscription<?>> getSubscriptions() {
        return subscriptions;
    }

    public List<SideComputer<?, ?, ?>> getSideComputers() {
        return sideComputers;
    }

    public EventConfiguration getGlobalEventConfiguration() {
        return globalEventConfiguration;
    }

    public RuntimeConfiguration getRuntimeConfiguration() {
        return runtimeConfiguration;
    }

    public boolean hasAsyncReactions() {
        return !subscriptions.isEmpty() || !sideComputers.isEmpty();
    }

    public static class Builder {
        private final List<EventSubscription<?>> subscriptions = new ArrayList<>();
        private final List<SideComputer<?, ?, ?>> sideComputers = new ArrayList<>();
        private EventConfiguration globalEventConfiguration;
        private RuntimeConfiguration runtimeConfiguration;

        public Builder subscription(EventSubscription<?> subscription) {
            if (subscription != null) {
                this.subscriptions.add(subscription);
            }
            return this;
        }

        public <T extends Event> Builder on(Class<T> eventType, EventReaction<? super T> reaction) {
            return subscription(EventSubscription.on(eventType, reaction));
        }

        public Builder sideComputer(SideComputer<?, ?, ?> sideComputer) {
            if (sideComputer != null) {
                this.sideComputers.add(sideComputer);
            }
            return this;
        }

        public Builder globalEventConfiguration(EventConfiguration eventConfiguration) {
            this.globalEventConfiguration = eventConfiguration;
            return this;
        }

        public Builder runtimeConfiguration(RuntimeConfiguration runtimeConfiguration) {
            this.runtimeConfiguration = runtimeConfiguration;
            return this;
        }

        public EventHandlingDefinition build() {
            return new EventHandlingDefinition(subscriptions, sideComputers, globalEventConfiguration,
                    runtimeConfiguration);
        }
    }

    /**
     * Configuration of globally applied event features for a pipeline.
     */
    public static class EventConfiguration {
        private final boolean eventOnParameterChanged;
        private final EventPayloadPolicy eventPayloadPolicy;

        private EventConfiguration(boolean eventOnParameterChanged, EventPayloadPolicy eventPayloadPolicy) {
            this.eventOnParameterChanged = eventOnParameterChanged;
            this.eventPayloadPolicy = eventPayloadPolicy != null ? eventPayloadPolicy
                    : EventPayloadPolicy.passthrough();
        }

        public static Builder builder() {
            return new Builder();
        }

        public boolean isEventOnParameterChanged() {
            return eventOnParameterChanged;
        }

        public EventPayloadPolicy getEventPayloadPolicy() {
            return eventPayloadPolicy;
        }

        public static class Builder {
            private boolean eventOnParameterChanged;
            private EventPayloadPolicy eventPayloadPolicy;

            public Builder eventOnParameterChanged(boolean eventOnParameterChanged) {
                this.eventOnParameterChanged = eventOnParameterChanged;
                return this;
            }

            public Builder eventPayloadPolicy(EventPayloadPolicy eventPayloadPolicy) {
                this.eventPayloadPolicy = eventPayloadPolicy;
                return this;
            }

            public EventConfiguration build() {
                return new EventConfiguration(eventOnParameterChanged, eventPayloadPolicy);
            }
        }
    }

    /**
     * Runtime configuration of the asynchronous event dispatcher.
     *
     * <p>
     * Unless an explicit executor is provided, the runtime uses a shared bounded
     * executor across runs. This default favors predictable resource usage over
     * guaranteed acceptance. If the executor saturates, reactions may be rejected
     * and dropped.
     * </p>
     */
    public static class RuntimeConfiguration {
        private static final ExecutorService DEFAULT_SHARED_REACTION_EXECUTOR = createDefaultSharedReactionExecutor();
        private final Supplier<ExecutorService> perRunReactionExecutorFactory;
        private final ExecutorService sharedReactionExecutor;
        private final Duration shutdownTimeout;
        private final ShutdownMode shutdownMode;

        private RuntimeConfiguration(Supplier<ExecutorService> perRunReactionExecutorFactory,
                                     ExecutorService sharedReactionExecutor,
                                     Duration shutdownTimeout,
                                     ShutdownMode shutdownMode) {
            this.perRunReactionExecutorFactory = perRunReactionExecutorFactory;
            this.sharedReactionExecutor = sharedReactionExecutor;
            this.shutdownTimeout = shutdownTimeout != null ? shutdownTimeout : Duration.ofSeconds(10);
            this.shutdownMode = shutdownMode != null ? shutdownMode : ShutdownMode.WAIT_FOR_DRAIN;
        }

        public static Builder builder() {
            return new Builder();
        }

        private static ExecutorService createDefaultSharedReactionExecutor() {
            int processors = Math.max(2, Runtime.getRuntime().availableProcessors());
            int corePoolSize = Math.min(processors, 8);
            int maximumPoolSize = Math.max(corePoolSize, corePoolSize * 4);
            ThreadPoolExecutor executor = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, 60L, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(2048), new Gear4jEventThreadFactory(),
                    new ThreadPoolExecutor.AbortPolicy());
            executor.allowCoreThreadTimeOut(true);
            return executor;
        }

        public ExecutorHandle acquireReactionExecutor() {
            if (sharedReactionExecutor != null) {
                return new ExecutorHandle(sharedReactionExecutor, false);
            }
            if (perRunReactionExecutorFactory != null) {
                return new ExecutorHandle(perRunReactionExecutorFactory.get(), true);
            }
            return new ExecutorHandle(DEFAULT_SHARED_REACTION_EXECUTOR, false);
        }

        public Duration getShutdownTimeout() {
            return shutdownTimeout;
        }

        public ShutdownMode getShutdownMode() {
            return shutdownMode;
        }

        /**
         * Shutdown behavior for the asynchronous event runtime.
         */
        public enum ShutdownMode {
            WAIT_FOR_DRAIN, DETACH_AND_DRAIN, CANCEL_PENDING_TASKS
        }

        public record ExecutorHandle(ExecutorService executorService, boolean shutdownOnClose) {
        }

        private static final class Gear4jEventThreadFactory implements ThreadFactory {
            private static final AtomicInteger COUNTER = new AtomicInteger();

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "gear4j-event-reaction-" + COUNTER.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        }

        public static class Builder {
            private Supplier<ExecutorService> perRunReactionExecutorFactory;
            private ExecutorService sharedReactionExecutor;
            private Duration shutdownTimeout;
            private ShutdownMode shutdownMode;

            /**
             * Backward-compatible alias for configuring one dedicated executor per run.
             *
             * <p>
             * A per-run executor provides stronger isolation between runs, but it also
             * means more executors are created over time. Reactions still remain
             * best-effort and may be dropped if the executor rejects submissions.
             * </p>
             */
            public Builder reactionExecutorFactory(Supplier<ExecutorService> reactionExecutorFactory) {
                return perRunReactionExecutorFactory(reactionExecutorFactory);
            }

            /**
             * Configures one dedicated reaction executor per run.
             *
             * <p>
             * This can be useful when runs must not share capacity. The caller remains
             * responsible for choosing a suitable bounded or unbounded strategy.
             * </p>
             */
            public Builder perRunReactionExecutorFactory(Supplier<ExecutorService> reactionExecutorFactory) {
                this.perRunReactionExecutorFactory = Objects.requireNonNull(reactionExecutorFactory,
                                                                            "reactionExecutorFactory");
                this.sharedReactionExecutor = null;
                return this;
            }

            /**
             * Configures a shared reaction executor reused across runs.
             *
             * <p>
             * This is the recommended model for most applications because it avoids
             * creating one pool per run and makes global capacity easier to reason about.
             * If the shared executor saturates, reactions may be rejected and dropped.
             * </p>
             */
            public Builder sharedReactionExecutor(ExecutorService sharedReactionExecutor) {
                this.sharedReactionExecutor = Objects.requireNonNull(sharedReactionExecutor, "sharedReactionExecutor");
                this.perRunReactionExecutorFactory = null;
                return this;
            }

            public Builder shutdownTimeout(Duration shutdownTimeout) {
                this.shutdownTimeout = shutdownTimeout;
                return this;
            }

            /**
             * Configures how shutdown behaves once the pipeline itself has completed.
             *
             * <p>
             * Drain modes wait for or detach from already accepted work. They do not
             * upgrade the runtime to guaranteed delivery: a saturated executor may still
             * have rejected some reactions earlier.
             * </p>
             */
            public Builder shutdownMode(ShutdownMode shutdownMode) {
                this.shutdownMode = shutdownMode;
                return this;
            }

            public RuntimeConfiguration build() {
                return new RuntimeConfiguration(perRunReactionExecutorFactory, sharedReactionExecutor, shutdownTimeout,
                        shutdownMode);
            }
        }
    }
}
