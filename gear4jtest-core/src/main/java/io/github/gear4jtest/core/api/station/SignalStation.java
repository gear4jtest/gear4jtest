package io.github.gear4jtest.core.api.station;

import java.util.Objects;
import java.util.function.Predicate;

import io.github.gear4jtest.core.api.context.ExecutionContext;

public class SignalStation<IN> extends AbstractStation<IN, IN> {
    private final StationSignalType signalType;
    private final Predicate<SignalInterpretationContext<IN>> condition;

    private SignalStation(String id,
                          StationSignalType signalType,
                          Predicate<SignalInterpretationContext<IN>> condition) {
        super(id, StationKind.SIGNAL, null, null, null, true, null, null);
        this.signalType = Objects.requireNonNull(signalType, "signalType is required");
        this.condition = Objects.requireNonNull(condition, "condition is required");
    }

    public StationSignalType getSignalType() {
        return signalType;
    }

    public Predicate<SignalInterpretationContext<IN>> getCondition() {
        return condition;
    }

    public static class Builder<IN> {
        private String id = "";
        private StationSignalType signalType;
        private Predicate<SignalInterpretationContext<IN>> condition;

        public Builder<IN> id(String id) {
            this.id = Objects.requireNonNull(id, "id is required");
            return this;
        }

        public Builder<IN> type(StationSignalType signalType) {
            this.signalType = Objects.requireNonNull(signalType, "signalType is required");
            return this;
        }

        public Builder<IN> condition(Predicate<SignalInterpretationContext<IN>> condition) {
            this.condition = condition;
            return this;
        }

        public SignalStation<IN> build() {
            return new SignalStation<>(id, signalType != null ? signalType : StationSignalType.FATAL,
                    condition != null ? condition : ignored -> true);
        }
    }

    public static class SignalInterpretationContext<T> {
        private final T item;
        private final ExecutionContext itemExecution;

        public SignalInterpretationContext(T item, ExecutionContext itemExecution) {
            this.item = item;
            this.itemExecution = itemExecution;
        }

        public T getItem() {
            return item;
        }

        public ExecutionContext getItemExecution() {
            return itemExecution;
        }
    }
}
