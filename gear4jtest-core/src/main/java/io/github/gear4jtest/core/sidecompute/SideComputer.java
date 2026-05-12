package io.github.gear4jtest.core.sidecompute;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;

import io.github.gear4jtest.core.event.Event;
import io.github.gear4jtest.core.event.EventSubscription;
import io.github.gear4jtest.core.event.StationFinishedEvent;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import io.github.gear4jtest.core.model.StationLogStatus;

public final class SideComputer<E extends Event, T, R> {

    private final Class<E> eventType;
    private final Predicate<E> trigger;
    private final String key;
    private final Function<E, T> computer;
    private final Function<T, R> mapper;
    private final List<SideComputeHandler<E, T>> handlers;

    private SideComputer(Class<E> eventType,
                         Predicate<E> trigger,
                         String key,
                         Function<E, T> computer,
                         Function<T, R> mapper,
                         List<SideComputeHandler<E, T>> handlers) {
        this.eventType = Objects.requireNonNull(eventType, "eventType");
        this.trigger = trigger != null ? trigger : __ -> true;
        this.key = Objects.requireNonNull(key, "key");
        this.computer = Objects.requireNonNull(computer, "computer");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.handlers = List.copyOf(handlers);
    }

    public static <E extends Event, T> Builder<E, T, T> onEvent(Class<E> eventType, String key) {
        return new Builder<>(eventType, key);
    }

    public static <T> Builder<StationFinishedEvent, T, T> onStationFinished(String operationId, String key) {
        Builder<StationFinishedEvent, T, T> builder = SideComputer
                .<StationFinishedEvent, T>onEvent(StationFinishedEvent.class, key);
        return builder.filter(event -> operationId.equals(event.getOperationId()));
    }

    public static <T> Builder<StationFinishedEvent, T, T> onStationSuccess(String operationId, String key) {
        return onStationStatus(operationId, StationLogStatus.SUCCEEDED, key);
    }

    public static <T> Builder<StationFinishedEvent, T, T> onStationFailure(String operationId, String key) {
        return onStationStatus(operationId, StationLogStatus.FAILED, key);
    }

    public static <T> Builder<StationFinishedEvent, T, T> onStationStatus(String operationId,
                                                                          StationLogStatus status,
                                                                          String key) {
        Builder<StationFinishedEvent, T, T> builder = SideComputer
                .<StationFinishedEvent, T>onEvent(StationFinishedEvent.class, key);
        return builder.filter(event -> operationId.equals(event.getOperationId()))
                .filter(event -> event.getStatus() == status);
    }

    public EventSubscription<E> toSubscription(ExecutionContextRegistry registry) {
        return EventSubscription.on(eventType, trigger, event -> runCompute(event, registry));
    }

    public String key() {
        return key;
    }

    private void runCompute(E event, ExecutionContextRegistry registry) {
        var executionContext = registry.get(event.getExecutionId());
        if (executionContext == null) {
            return;
        }

        SideComputeContext sideComputeContext = executionContext.getSideComputeContext();
        CompletableFuture<R> future = sideComputeContext.getOrCreateFuture(key);

        try {
            T computeResult = computer.apply(event);
            for (SideComputeHandler<E, T> handler : handlers) {
                handler.handle(key, event, computeResult, executionContext);
            }
            R finalResult = mapper.apply(computeResult);
            future.complete(finalResult);
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
        }
    }

    public static final class Builder<E extends Event, T, R> {

        private final Class<E> eventType;
        private final String key;
        private final List<SideComputeHandler<E, T>> handlers = new ArrayList<>();
        private Predicate<E> trigger = __ -> true;
        private Function<E, T> computer;
        @SuppressWarnings("unchecked")
        private Function<T, R> mapper = value -> (R) value;

        private Builder(Class<E> eventType, String key) {
            this.eventType = Objects.requireNonNull(eventType, "eventType");
            this.key = Objects.requireNonNull(key, "key");
        }

        public Builder<E, T, R> filter(Predicate<E> predicate) {
            Objects.requireNonNull(predicate, "predicate");
            this.trigger = this.trigger.and(predicate);
            return this;
        }

        public Builder<E, T, R> computer(Function<E, T> computer) {
            this.computer = Objects.requireNonNull(computer, "computer");
            return this;
        }

        public Builder<E, T, R> addHandler(SideComputeHandler<E, T> handler) {
            if (handler != null) {
                handlers.add(handler);
            }
            return this;
        }

        public <NEW_R> Builder<E, T, NEW_R> map(Function<T, NEW_R> mapper) {
            Builder<E, T, NEW_R> next = new Builder<>(eventType, key);
            next.trigger = this.trigger;
            next.computer = this.computer;
            next.handlers.addAll(this.handlers);
            next.mapper = Objects.requireNonNull(mapper, "mapper");
            return next;
        }

        public SideComputer<E, T, R> build() {
            return new SideComputer<>(eventType, trigger, key, computer, mapper, handlers);
        }
    }
}
