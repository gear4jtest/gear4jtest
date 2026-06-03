package io.github.gear4jtest.core.api.station;

import java.util.function.Predicate;

import io.github.gear4jtest.core.api.behavior.SignalType;
import io.github.gear4jtest.core.api.context.ExecutionContext;

public class SignalStation<IN> extends AbstractStation<IN, IN> {
    protected SignalType signalType;
    protected Predicate<SignalInterpretationContext<IN>> condition;

    public SignalStation() {
        super("", StationKind.SIGNAL);
    }

    public SignalType getSignalType() {
        return signalType;
    }

    public Predicate<SignalInterpretationContext<IN>> getCondition() {
        return condition;
    }

    public static class Builder<IN> {
        private String id = "";
        private SignalType signalType;
        private Predicate<SignalInterpretationContext<IN>> condition;

        public Builder<IN> id(String id) {
            this.id = java.util.Objects.requireNonNull(id, "id is required");
            return this;
        }

        public Builder<IN> type(SignalType signalType) {
            this.signalType = signalType;
            return this;
        }

        public Builder<IN> condition(Predicate<SignalInterpretationContext<IN>> condition) {
            this.condition = condition;
            return this;
        }

        public SignalStation<IN> build() {
            SignalStation<IN> station = new SignalStation<>();
            station.id = id;
            station.signalType = signalType;
            station.condition = condition;
            return station;
        }
    }

    public static class SignalInterpretationContext<T> {
        private T item;
        private ExecutionContext itemExecution;

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
