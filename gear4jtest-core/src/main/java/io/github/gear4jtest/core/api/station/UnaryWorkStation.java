package io.github.gear4jtest.core.api.station;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import io.github.gear4jtest.core.api.StationMetadata;
import io.github.gear4jtest.core.api.behavior.BaseError;
import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.behavior.Processor;
import io.github.gear4jtest.core.api.behavior.StationSkipTest;
import io.github.gear4jtest.core.api.behavior.StationSkipper;
import io.github.gear4jtest.core.api.context.ParameterResolutionContext;
import io.github.gear4jtest.core.api.context.ParameterResolutionContextParameterModel;
import io.github.gear4jtest.core.api.context.StationParameterModel;

public class UnaryWorkStation<INOUT> extends WorkStation<INOUT, INOUT> {
    private UnaryWorkStation(String id,
                             Class<Operator<INOUT, INOUT>> type,
                             List<StationParameterModel<?, ?>> parameters,
                             List<Processor> processors,
                             List<BaseError<INOUT>> onErrors,
                             Operator<INOUT, INOUT> fallbackOperator,
                             List<StationSkipper> skippers) {
        super(id, type, parameters, processors, onErrors, fallbackOperator, false, skippers, StationMetadata.empty(),
                true);
    }

    public static class Builder<INOUT, OP extends Operator<INOUT, INOUT>> {
        private String id = "";
        private Class<? extends Operator<?, ?>> type;
        private final List<StationParameterModel<?, ?>> parameters;
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
            this.parameters = new ArrayList<>(source.parameters);
            this.processors = new ArrayList<>(source.processors);
            this.onErrors = new ArrayList<>(source.onErrors);
            this.skippers = new ArrayList<>(source.skippers);
            this.fallbackOperator = source.fallbackOperator;
        }

        public <T extends Operator<INOUT, INOUT>> Builder<INOUT, T> type(Class<T> type) {
            Builder<INOUT, T> next = new Builder<>(this);
            next.type = Objects.requireNonNull(type, "operator type must not be null");
            return next;
        }

        public Builder<INOUT, OP> id(String id) {
            this.id = id;
            return this;
        }

        /**
         * Binds an operator parameter to a fixed value.
         */
        public <A> Builder<INOUT, OP> parameter(WorkStation.ParamRetriever<OP, A> retriever, A value) {

            parameters.add(new ParameterResolutionContextParameterModel<>(retriever, ctx -> value));
            return this;
        }

        /**
         * Binds an operator parameter to a context-independent supplier.
         */
        public <A> Builder<INOUT, OP> parameter(WorkStation.ParamRetriever<OP, A> retriever,
                                                java.util.function.Supplier<A> supplier) {

            parameters.add(new ParameterResolutionContextParameterModel<>(retriever,
                    ctx -> supplier.get()));
            return this;
        }

        /**
         * Binds an operator parameter to a resolver that can inspect the current input
         * and execution context.
         */
        public <A> Builder<INOUT, OP> parameter(WorkStation.ParamRetriever<OP, A> retriever,
                                                Function<ParameterResolutionContext<INOUT>, A> resolver) {

            parameters.add(new ParameterResolutionContextParameterModel<>(retriever, resolver));
            return this;
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

        @SuppressWarnings({ "unchecked", "rawtypes" })
        public UnaryWorkStation<INOUT> build() {
            return new UnaryWorkStation<>(id,
                    (Class) type,
                    parameters,
                    processors,
                    onErrors,
                    (Operator<INOUT, INOUT>) fallbackOperator,
                    skippers);
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
}
