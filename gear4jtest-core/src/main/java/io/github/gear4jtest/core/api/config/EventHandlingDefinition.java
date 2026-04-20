package io.github.gear4jtest.core.api.config;

import io.github.gear4jtest.core.event.Event;
import io.github.gear4jtest.core.event.EventPayloadPolicy;
import io.github.gear4jtest.core.event.EventReaction;
import io.github.gear4jtest.core.event.EventSubscription;
import io.github.gear4jtest.core.sidecompute.SideComputer;
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

public class EventHandlingDefinition {

    private final List<EventSubscription<?>> subscriptions;
    private final List<SideComputer<?, ?, ?>> sideComputers;
    private final EventConfiguration globalEventConfiguration;
    private final RuntimeConfiguration runtimeConfiguration;

    private EventHandlingDefinition(
            List<EventSubscription<?>> subscriptions,
            List<SideComputer<?, ?, ?>> sideComputers,
            EventConfiguration globalEventConfiguration,
            RuntimeConfiguration runtimeConfiguration) {
        this.subscriptions = subscriptions != null ? List.copyOf(subscriptions) : List.of();
        this.sideComputers = sideComputers != null ? List.copyOf(sideComputers) : List.of();
        this.globalEventConfiguration =
                globalEventConfiguration != null ? globalEventConfiguration : EventConfiguration.builder().build();
        this.runtimeConfiguration =
                runtimeConfiguration != null ? runtimeConfiguration : RuntimeConfiguration.builder().build();
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

    public static Builder builder() {
        return new Builder();
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
            return new EventHandlingDefinition(
                    subscriptions, sideComputers, globalEventConfiguration, runtimeConfiguration);
        }
    }

    public static class EventConfiguration {

        private final boolean eventOnParameterChanged;
        private final EventPayloadPolicy eventPayloadPolicy;

        private EventConfiguration(boolean eventOnParameterChanged, EventPayloadPolicy eventPayloadPolicy) {
            this.eventOnParameterChanged = eventOnParameterChanged;
            this.eventPayloadPolicy = eventPayloadPolicy != null ? eventPayloadPolicy : EventPayloadPolicy.passthrough();
        }

        public boolean isEventOnParameterChanged() {
            return eventOnParameterChanged;
        }

        public EventPayloadPolicy getEventPayloadPolicy() {
            return eventPayloadPolicy;
        }

        public static Builder builder() {
            return new Builder();
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

    public static class RuntimeConfiguration {

        public enum ShutdownMode {
            WAIT_FOR_DRAIN,
            DETACH_AND_DRAIN,
            CANCEL_PENDING_TASKS
        }

        public record ExecutorHandle(ExecutorService executorService, boolean shutdownOnClose) {}

        private static final ExecutorService DEFAULT_SHARED_REACTION_EXECUTOR = createDefaultSharedReactionExecutor();

        private final Supplier<ExecutorService> perRunReactionExecutorFactory;
        private final ExecutorService sharedReactionExecutor;
        private final Duration shutdownTimeout;
        private final ShutdownMode shutdownMode;

        private RuntimeConfiguration(
                Supplier<ExecutorService> perRunReactionExecutorFactory,
                ExecutorService sharedReactionExecutor,
                Duration shutdownTimeout,
                ShutdownMode shutdownMode) {
            this.perRunReactionExecutorFactory = perRunReactionExecutorFactory;
            this.sharedReactionExecutor = sharedReactionExecutor;
            this.shutdownTimeout = shutdownTimeout != null ? shutdownTimeout : Duration.ofSeconds(10);
            this.shutdownMode = shutdownMode != null ? shutdownMode : ShutdownMode.WAIT_FOR_DRAIN;
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

        public static Builder builder() {
            return new Builder();
        }

        private static ExecutorService createDefaultSharedReactionExecutor() {
            int processors = Math.max(2, Runtime.getRuntime().availableProcessors());
            int corePoolSize = Math.min(processors, 8);
            int maximumPoolSize = Math.max(corePoolSize, corePoolSize * 4);
            ThreadPoolExecutor executor = new ThreadPoolExecutor(
                    corePoolSize,
                    maximumPoolSize,
                    60L,
                    TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(2048),
                    new Gear4jEventThreadFactory(),
                    new ThreadPoolExecutor.AbortPolicy());
            executor.allowCoreThreadTimeOut(true);
            return executor;
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
             * Backward-compatible alias: providing a factory means one dedicated executor per run.
             */
            public Builder reactionExecutorFactory(Supplier<ExecutorService> reactionExecutorFactory) {
                return perRunReactionExecutorFactory(reactionExecutorFactory);
            }

            public Builder perRunReactionExecutorFactory(Supplier<ExecutorService> reactionExecutorFactory) {
                this.perRunReactionExecutorFactory =
                        Objects.requireNonNull(reactionExecutorFactory, "reactionExecutorFactory");
                this.sharedReactionExecutor = null;
                return this;
            }

            public Builder sharedReactionExecutor(ExecutorService sharedReactionExecutor) {
                this.sharedReactionExecutor = Objects.requireNonNull(sharedReactionExecutor, "sharedReactionExecutor");
                this.perRunReactionExecutorFactory = null;
                return this;
            }

            public Builder shutdownTimeout(Duration shutdownTimeout) {
                this.shutdownTimeout = shutdownTimeout;
                return this;
            }

            public Builder shutdownMode(ShutdownMode shutdownMode) {
                this.shutdownMode = shutdownMode;
                return this;
            }

            public RuntimeConfiguration build() {
                return new RuntimeConfiguration(
                        perRunReactionExecutorFactory,
                        sharedReactionExecutor,
                        shutdownTimeout,
                        shutdownMode);
            }
        }
    }
}
