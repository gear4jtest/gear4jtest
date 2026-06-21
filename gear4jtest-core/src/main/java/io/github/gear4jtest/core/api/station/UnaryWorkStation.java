package io.github.gear4jtest.core.api.station;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import io.github.gear4jtest.core.api.behavior.BaseError;
import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.behavior.Processor;
import io.github.gear4jtest.core.api.behavior.StationSkipTest;
import io.github.gear4jtest.core.api.behavior.StationSkipper;
import io.github.gear4jtest.core.engine.support.WorkerParamsInjector;

public class UnaryWorkStation<INOUT> extends WorkStation<INOUT, INOUT> {
    private UnaryWorkStation() {
        super();
    }

    public static class Builder<INOUT, OP extends Operator<INOUT, INOUT>> {
        private String id = "";
        private Class<? extends Operator<?, ?>> type;
        private final List<WorkerParamsInjector.ParameterModel<?, ?>> parameters;
        private final List<Processor> processors;
        private final List<BaseError<INOUT>> onErrors;
        private final List<StationSkipper> skippers;
        private Operator<?, ?> fallbackOperator;

        public Builder() {
            this.parameters = new ArrayList<>();
            this.processors = new ArrayList<>();
            this.onErrors = new ArrayList<>();
            this.skippers = new ArrayList<>();
        }

        private <PREVIOUS_OP extends Operator<INOUT, INOUT>> Builder(Builder<INOUT, PREVIOUS_OP> source) {
            this.id = source.id;
            this.type = source.type;
            this.parameters = source.parameters;
            this.processors = source.processors;
            this.onErrors = source.onErrors;
            this.skippers = source.skippers;
            this.fallbackOperator = source.fallbackOperator;
        }

        public <A, T extends Operator<INOUT, INOUT>> Builder<INOUT, T> type(Class<T> type) {
            this.type = type;
            return new Builder<>(this);
        }

        public Builder<INOUT, OP> id(String id) {
            this.id = id;
            return this;
        }

        /**
         * Binds an operator parameter to a fixed value.
         */
        public <A> Builder<INOUT, OP> parameter(WorkStation.ParamRetriever<OP, A> retriever, A value) {

            addParameterInjectorIfNecessary();
            parameters.add(new WorkerParamsInjector.InterpretationContextParameterModel<>(retriever, ctx -> value));
            return this;
        }

        /**
         * Binds an operator parameter to a context-independent supplier.
         */
        public <A> Builder<INOUT, OP> parameter(WorkStation.ParamRetriever<OP, A> retriever,
                                                java.util.function.Supplier<A> supplier) {

            addParameterInjectorIfNecessary();
            parameters.add(new WorkerParamsInjector.InterpretationContextParameterModel<>(retriever,
                    ctx -> supplier.get()));
            return this;
        }

        /**
         * Binds an operator parameter to a resolver that can inspect the current input
         * and execution context.
         */
        public <A> Builder<INOUT, OP> parameter(WorkStation.ParamRetriever<OP, A> retriever,
                                                Function<WorkerParamsInjector.InterpretationContext<INOUT>, A> resolver) {

            addParameterInjectorIfNecessary();
            parameters.add(new WorkerParamsInjector.InterpretationContextParameterModel<>(retriever, resolver));
            return this;
        }

        private void addParameterInjectorIfNecessary() {
            if (processors.stream().noneMatch(WorkerParamsInjector.class::isInstance)) {
                processors.add(new WorkerParamsInjector());
            }
        }

        public Builder<INOUT, OP> addProcessor(Processor processor) {
            processors.add(processor);
            return this;
        }

        public Builder<INOUT, OP> onError(BaseError.SafeError<INOUT> onError) {
            onErrors.add(onError);
            return this;
        }

        public UnsafeOperation.Builder<INOUT, OP> onError(BaseError.UnSafeError<INOUT> onError) {
            onErrors.add(onError);
            return new UnsafeOperation.Builder<>(this);
        }

        public Builder<INOUT, OP> fallback(Operator<INOUT, INOUT> operator) {
            fallbackOperator = operator;
            return this;
        }

        /**
         * Adds a pre-processor skip rule evaluated before station preparation.
         *
         * <p>
         * Unary stations do not need a fallback transformer to remain type-safe when
         * skipped: the input can be carried forward unchanged while the station trace
         * remains {@code SKIPPED}.
         * </p>
         */
        public Builder<INOUT, OP> skipIf(StationSkipTest predicate) {
            skippers.add(StationSkipper.pre(predicate));
            return this;
        }

        /**
         * Adds a post-processor skip rule evaluated after parameter resolution and
         * side-compute waits.
         */
        public Builder<INOUT, OP> skipIfPost(StationSkipTest predicate) {
            skippers.add(StationSkipper.post(predicate));
            return this;
        }

        public UnaryWorkStation<INOUT> build() {
            UnaryWorkStation<INOUT> station = new UnaryWorkStation<>();
            applyBuilder(station, this);
            return station;
        }
    }

    public static class UnsafeOperation<INOUT, OP extends Operator<INOUT, INOUT>> {
        public static class Builder<INOUT, OP extends Operator<INOUT, INOUT>> {
            private final UnaryWorkStation.Builder<INOUT, OP> operation;

            public Builder(UnaryWorkStation.Builder<INOUT, OP> operation) {
                this.operation = operation;
            }

            public Builder<INOUT, OP> onError(BaseError.SafeError<INOUT> onError) {
                operation.onError(onError);
                return this;
            }

            public Builder<INOUT, OP> onError(BaseError.UnSafeError<INOUT> onError) {
                operation.onError(onError);
                return this;
            }

            public SafeOperation.Builder<INOUT, OP> transformer(Operator<INOUT, INOUT> operator) {
                operation.fallback(operator);
                return new SafeOperation.Builder<>(operation);
            }
        }
    }

    public static class SafeOperation<INOUT, OP extends Operator<INOUT, INOUT>> {
        public static class Builder<INOUT, OP extends Operator<INOUT, INOUT>> {
            private final UnaryWorkStation.Builder<INOUT, OP> operation;

            public Builder(UnaryWorkStation.Builder<INOUT, OP> operation) {
                this.operation = operation;
            }

            public Builder<INOUT, OP> onError(BaseError.SafeError<INOUT> onError) {
                operation.onError(onError);
                return this;
            }

            public Builder<INOUT, OP> onError(BaseError.UnSafeError<INOUT> onError) {
                operation.onError(onError);
                return this;
            }

            public UnaryWorkStation.Builder<INOUT, OP> transformer(Operator<INOUT, INOUT> operator) {
                operation.fallback(operator);
                return operation;
            }

            /**
             * Adds a pre-processor skip rule evaluated before station preparation.
             */
            public Builder<INOUT, OP> skipIf(StationSkipTest predicate) {
                operation.skipIf(predicate);
                return this;
            }

            /**
             * Adds a post-processor skip rule evaluated after parameter resolution and
             * side-compute waits.
             */
            public Builder<INOUT, OP> skipIfPost(StationSkipTest predicate) {
                operation.skipIfPost(predicate);
                return this;
            }

            public UnaryWorkStation<INOUT> build() {
                return operation.build();
            }
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static <INOUT, OP extends Operator<INOUT, INOUT>> void applyBuilder(UnaryWorkStation<INOUT> station,
                                                                                Builder<INOUT, OP> builder) {
        station.id = builder.id;
        station.unary = true;
        station.type = (Class) builder.type;
        station.processors = builder.processors.isEmpty() ? null : new ArrayList<>(builder.processors);
        station.parameters = builder.parameters.isEmpty() ? null : new ArrayList<>(builder.parameters);
        station.onErrors = builder.onErrors.isEmpty() ? null : new ArrayList<>(builder.onErrors);
        station.fallbackOperator = (Operator<INOUT, INOUT>) builder.fallbackOperator;
        builder.skippers.forEach(station::addSkipper);
    }
}
