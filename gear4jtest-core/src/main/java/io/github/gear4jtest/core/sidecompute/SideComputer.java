package io.github.gear4jtest.core.sidecompute;

import io.github.gear4jtest.core.event.OperationCompletedEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public final class SideComputer<T, R> {

    private final String operationId;
    private final String key;
    private final Function<OperationCompletedEvent, T> computer;
    private final Function<T, R> mapper;
    private final List<SideComputeHandler<T>> handlers;

    private SideComputer(
            String operationId,
            String key,
            Function<OperationCompletedEvent, T> computer,
            Function<T, R> mapper,
            List<SideComputeHandler<T>> handlers) {
        this.operationId = Objects.requireNonNull(operationId, "operationId");
        this.key = Objects.requireNonNull(key, "key");
        this.computer = Objects.requireNonNull(computer, "computer");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.handlers = List.copyOf(handlers);
    }

    public static <T> Builder<T, T> builder(String operationId, String key) {
        return new Builder<>(operationId, key);
    }

    public boolean matches(OperationCompletedEvent ev) {
        return ev != null && operationId.equals(ev.getOperationId());
    }

    public String key() {
        return key;
    }

    public Function<OperationCompletedEvent, T> computer() {
        return computer;
    }

    public Function<T, R> mapper() {
        return mapper;
    }

    public List<SideComputeHandler<T>> handlers() {
        return handlers;
    }

    public static final class Builder<T, R> {

        private final String operationId;
        private final String key;
        private Function<OperationCompletedEvent, T> computer;
        @SuppressWarnings("unchecked")
        private Function<T, R> mapper = t -> (R) t;
        private final List<SideComputeHandler<T>> handlers = new ArrayList<>();

        private Builder(String operationId, String key) {
            this.operationId = Objects.requireNonNull(operationId, "operationId");
            this.key = Objects.requireNonNull(key, "key");
        }

        public Builder<T, R> computer(Function<OperationCompletedEvent, T> computer) {
            this.computer = Objects.requireNonNull(computer, "computer");
            return this;
        }

        public Builder<T, R> addHandler(SideComputeHandler<T> handler) {
            if (handler != null) {
                handlers.add(handler);
            }
            return this;
        }

        public <NEW_R> Builder<T, NEW_R> map(Function<T, NEW_R> mapper) {
            Builder<T, NEW_R> next = new Builder<>(operationId, key);
            next.computer = this.computer;
            next.handlers.addAll(this.handlers);
            next.mapper = Objects.requireNonNull(mapper, "mapper");
            return next;
        }

        public SideComputer<T, R> build() {
            return new SideComputer<>(operationId, key, computer, mapper, handlers);
        }
    }
}
