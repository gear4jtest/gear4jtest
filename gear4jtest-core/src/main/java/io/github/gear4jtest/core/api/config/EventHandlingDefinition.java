package io.github.gear4jtest.core.api.config;

import io.github.gear4jtest.core.event.Event;
import io.github.gear4jtest.core.event.EventReaction;
import io.github.gear4jtest.core.event.EventSubscription;
import io.github.gear4jtest.core.sidecompute.SideComputer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
        this.globalEventConfiguration = globalEventConfiguration != null
                ? globalEventConfiguration
                : EventConfiguration.builder().build();
        this.runtimeConfiguration = runtimeConfiguration != null
                ? runtimeConfiguration
                : RuntimeConfiguration.builder().build();
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
                    subscriptions,
                    sideComputers,
                    globalEventConfiguration,
                    runtimeConfiguration);
        }
    }

    public static class EventConfiguration {

        private final boolean eventOnParameterChanged;

        private EventConfiguration(boolean eventOnParameterChanged) {
            this.eventOnParameterChanged = eventOnParameterChanged;
        }

        public boolean isEventOnParameterChanged() {
            return eventOnParameterChanged;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {

            private boolean eventOnParameterChanged;

            public Builder eventOnParameterChanged(boolean eventOnParameterChanged) {
                this.eventOnParameterChanged = eventOnParameterChanged;
                return this;
            }

            public EventConfiguration build() {
                return new EventConfiguration(eventOnParameterChanged);
            }
        }
    }

    public static class RuntimeConfiguration {

        public enum ShutdownMode {
            WAIT_FOR_SUBMITTED_TASKS,
            CANCEL_PENDING_TASKS
        }

        private final Supplier<ExecutorService> reactionExecutorFactory;
        private final Duration shutdownTimeout;
        private final ShutdownMode shutdownMode;

        private RuntimeConfiguration(
                Supplier<ExecutorService> reactionExecutorFactory,
                Duration shutdownTimeout,
                ShutdownMode shutdownMode) {
            this.reactionExecutorFactory = reactionExecutorFactory != null
                    ? reactionExecutorFactory
                    : Executors::newCachedThreadPool;
            this.shutdownTimeout = shutdownTimeout != null ? shutdownTimeout : Duration.ofSeconds(10);
            this.shutdownMode = shutdownMode != null ? shutdownMode : ShutdownMode.WAIT_FOR_SUBMITTED_TASKS;
        }

        public ExecutorService createReactionExecutor() {
            return reactionExecutorFactory.get();
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

        public static class Builder {

            private Supplier<ExecutorService> reactionExecutorFactory;
            private Duration shutdownTimeout;
            private ShutdownMode shutdownMode;

            public Builder reactionExecutorFactory(Supplier<ExecutorService> reactionExecutorFactory) {
                this.reactionExecutorFactory = Objects.requireNonNull(reactionExecutorFactory, "reactionExecutorFactory");
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
                return new RuntimeConfiguration(reactionExecutorFactory, shutdownTimeout, shutdownMode);
            }
        }
    }
}
