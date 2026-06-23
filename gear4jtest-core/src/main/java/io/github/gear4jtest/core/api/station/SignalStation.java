package io.github.gear4jtest.core.api.station;

import java.util.Objects;
import java.util.function.Predicate;

import io.github.gear4jtest.core.api.behavior.SignalType;
import io.github.gear4jtest.core.api.context.ExecutionContext;

public class SignalStation<IN> extends AbstractStation<IN, IN> {
    private final SignalType signalType;
    private final Predicate<SignalInterpretationContext<IN>> condition;

    private SignalStation(String id,
                          SignalType signalType,
                          Predicate<SignalInterpretationContext<IN>> condition) {
        super(id, StationKind.SIGNAL, null, null, null, true, null, null);
        this.signalType = requireStationSignal(signalType);
        this.condition = Objects.requireNonNull(condition, "condition is required");
    }

    /**
     * Returns the explicit flow signal emitted by this station.
     *
     * <p>
     * Signal stations only support {@link SignalType#STOP} and
     * {@link SignalType#FATAL}. {@link SignalType#IGNORE} is reserved for error
     * policies and is rejected by the builder.
     * </p>
     */
    public SignalType getSignalType() {
        return signalType;
    }

    public Predicate<SignalInterpretationContext<IN>> getCondition() {
        return condition;
    }

    private static SignalType requireStationSignal(SignalType signalType) {
        SignalType effectiveSignalType = Objects.requireNonNull(signalType, "signalType is required");
        if (effectiveSignalType == SignalType.IGNORE) {
            throw new IllegalArgumentException("SignalStation does not support IGNORE; use STOP or FATAL");
        }
        return effectiveSignalType;
    }

    public static class Builder<IN> {
        private String id = "";
        private SignalType signalType;
        private Predicate<SignalInterpretationContext<IN>> condition;

        public Builder<IN> id(String id) {
            this.id = Objects.requireNonNull(id, "id is required");
            return this;
        }

        public Builder<IN> type(SignalType signalType) {
            this.signalType = requireStationSignal(signalType);
            return this;
        }

        public Builder<IN> condition(Predicate<SignalInterpretationContext<IN>> condition) {
            this.condition = condition;
            return this;
        }

        public SignalStation<IN> build() {
            return new SignalStation<>(id, signalType != null ? signalType : SignalType.FATAL,
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
