package io.github.gear4jtest.core.api.station;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import io.github.gear4jtest.core.api.MutableStationMetadata;
import io.github.gear4jtest.core.api.StationMetadata;
import io.github.gear4jtest.core.api.behavior.BaseError;
import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.behavior.Processor;
import io.github.gear4jtest.core.api.behavior.StationSkipTest;
import io.github.gear4jtest.core.api.behavior.StationSkipper;
import io.github.gear4jtest.core.api.config.OperationAdditionalModel;
import io.github.gear4jtest.core.api.context.ParameterResolutionContext;
import io.github.gear4jtest.core.api.context.ParameterResolutionContextParameterModel;
import io.github.gear4jtest.core.api.context.StationParameter;
import io.github.gear4jtest.core.api.context.StationParameterModel;

public class WorkStation<IN, OUT> extends AbstractStation<IN, OUT> {
    private final Class<Operator<IN, OUT>> type;
    private final List<StationParameterModel<?, ?>> parameters;
    /**
     * Whether the operator instance is reused for the whole assembly line run.
     */
    private final boolean reuseOperatorInstanceWithinRun;

    WorkStation(String id,
                Class<Operator<IN, OUT>> type,
                List<StationParameterModel<?, ?>> parameters,
                List<Processor> processors,
                List<BaseError<IN>> onErrors,
                Operator<IN, OUT> fallbackOperator,
                boolean reuseOperatorInstanceWithinRun,
                List<StationSkipper> skippers,
                StationMetadata metadata,
                boolean unary) {
        super(id, StationKind.PROCESSING, processors, onErrors, fallbackOperator, unary, skippers, metadata);
        this.type = type;
        this.parameters = parameters == null || parameters.isEmpty() ? List.of() : List.copyOf(parameters);
        this.reuseOperatorInstanceWithinRun = reuseOperatorInstanceWithinRun;
    }

    public List<StationParameterModel<?, ?>> getParameters() {
        return parameters;
    }

    public Class<Operator<IN, OUT>> getType() {
        return type;
    }

    public boolean isReuseOperatorInstanceWithinRun() {
        return reuseOperatorInstanceWithinRun;
    }

    @FunctionalInterface
    public interface ParamRetriever<T extends Operator<?, ?>, U> {
        StationParameter<U> getParameterValue(T operation);
    }

    public static class Builder<IN, OUT, OP extends Operator<IN, OUT>> {
        private String id = "";
        private Class<? extends Operator<?, ?>> type;
        private final List<StationParameterModel<?, ?>> parameters;
        private final List<Processor> processors;
        private final List<BaseError<IN>> onErrors;
        private final List<StationSkipper> skippers;
        private final List<MetadataEntry<?>> metadata;
        private Operator<?, ?> fallbackOperator;
        private boolean reuseOperatorInstanceWithinRun;

        public Builder() {
            this.parameters = new ArrayList<>();
            this.processors = new ArrayList<>();
            this.onErrors = new ArrayList<>();
            this.skippers = new ArrayList<>();
            this.metadata = new ArrayList<>();
        }

        private <PREVIOUS_OUT, PREVIOUS_OP extends Operator<IN, PREVIOUS_OUT>> Builder(
                                                                                       Builder<IN, PREVIOUS_OUT, PREVIOUS_OP> source) {

            this.id = source.id;
            this.type = source.type;
            this.parameters = new ArrayList<>(source.parameters);
            this.processors = new ArrayList<>(source.processors);
            this.onErrors = new ArrayList<>(source.onErrors);
            this.skippers = new ArrayList<>(source.skippers);
            this.metadata = new ArrayList<>(source.metadata);
            this.fallbackOperator = source.fallbackOperator;
            this.reuseOperatorInstanceWithinRun = source.reuseOperatorInstanceWithinRun;
        }

        public <A, T extends Operator<IN, A>> Builder<IN, A, T> type(Class<T> type) {
            this.type = type;
            return new Builder<>(this);
        }

        public Builder<IN, OUT, OP> id(String id) {
            this.id = id;
            return this;
        }

        public Builder<IN, OUT, OP> reuseOperatorInstanceWithinRun() {
            this.reuseOperatorInstanceWithinRun = true;
            return this;
        }

        public Builder<IN, OUT, OP> reuseOperatorInstanceWithinRun(boolean enabled) {
            this.reuseOperatorInstanceWithinRun = enabled;
            return this;
        }

        /**
         * Binds an operator parameter to a fixed value.
         */
        public <A> Builder<IN, OUT, OP> parameter(ParamRetriever<OP, A> retriever, A value) {

            addParameter(new ParameterResolutionContextParameterModel<>(retriever, ctx -> value));
            return this;
        }

        /**
         * Binds an operator parameter to a context-independent supplier.
         */
        public <A> Builder<IN, OUT, OP> parameter(ParamRetriever<OP, A> retriever,
                                                  java.util.function.Supplier<A> supplier) {

            addParameter(new ParameterResolutionContextParameterModel<>(retriever,
                    ctx -> supplier.get()));
            return this;
        }

        /**
         * Binds an operator parameter to a resolver that can inspect the current input
         * and execution context.
         */
        public <A> Builder<IN, OUT, OP> parameter(ParamRetriever<OP, A> retriever,
                                                  Function<ParameterResolutionContext<IN>, A> resolver) {

            addParameter(new ParameterResolutionContextParameterModel<>(retriever, resolver));
            return this;
        }

        private void addParameter(StationParameterModel<OP, ?> parameterModel) {
            parameters.add(parameterModel);
        }

        public Builder<IN, OUT, OP> addProcessor(Processor processor) {
            processors.add(processor);
            return this;
        }

        public Builder<IN, OUT, OP> onError(BaseError.SafeError<IN> onError) {
            onErrors.add(onError);
            return this;
        }

        public UnsafeOperation.Builder<IN, OUT, OP> onError(BaseError.UnSafeError<IN> onError) {
            onErrors.add(onError);
            return new UnsafeOperation.Builder<>(this);
        }

        public Builder<IN, OUT, OP> fallback(Operator<IN, OUT> operator) {
            fallbackOperator = operator;
            return this;
        }

        /**
         * Adds a pre-processor skip rule evaluated before station preparation.
         */
        public UnsafeOperation.Builder<IN, OUT, OP> skipIf(StationSkipTest predicate) {
            skippers.add(StationSkipper.pre(predicate));
            return new UnsafeOperation.Builder<>(this);
        }

        /**
         * Adds a post-processor skip rule evaluated after parameter resolution and
         * side-compute waits.
         */
        public UnsafeOperation.Builder<IN, OUT, OP> skipIfPost(StationSkipTest predicate) {
            skippers.add(StationSkipper.post(predicate));
            return new UnsafeOperation.Builder<>(this);
        }

        public <T> Builder<IN, OUT, OP> metadata(Class<T> type, T value) {
            metadata.add(new MetadataEntry<>(type, value));
            return this;
        }

        public Builder<IN, OUT, OP> additionalModel(OperationAdditionalModel<IN, OUT, OP> model) {
            model.contributeTo(this);
            return this;
        }

        @SuppressWarnings({ "unchecked", "rawtypes" })
        public WorkStation<IN, OUT> build() {
            return new WorkStation<>(id,
                    (Class) type,
                    parameters,
                    processors,
                    onErrors,
                    (Operator<IN, OUT>) fallbackOperator,
                    reuseOperatorInstanceWithinRun,
                    skippers,
                    buildMetadata(metadata),
                    false);
        }
    }

    public static class UnsafeOperation<IN, OUT, OP extends Operator<IN, OUT>> {
        public static class Builder<IN, OUT, OP extends Operator<IN, OUT>> {
            private final WorkStation.Builder<IN, OUT, OP> operation;

            public Builder(WorkStation.Builder<IN, OUT, OP> operation) {
                this.operation = operation;
            }

            public Builder<IN, OUT, OP> onError(BaseError.SafeError<IN> onError) {
                operation.onError(onError);
                return this;
            }

            public UnsafeOperation.Builder<IN, OUT, OP> onError(BaseError.UnSafeError<IN> onError) {
                operation.onError(onError);
                return this;
            }

            /**
             * Adds a pre-processor skip rule evaluated before station preparation.
             */
            public UnsafeOperation.Builder<IN, OUT, OP> skipIf(StationSkipTest predicate) {
                operation.skipIf(predicate);
                return this;
            }

            /**
             * Adds a post-processor skip rule evaluated after parameter resolution and
             * side-compute waits.
             */
            public UnsafeOperation.Builder<IN, OUT, OP> skipIfPost(StationSkipTest predicate) {
                operation.skipIfPost(predicate);
                return this;
            }

            public SafeOperation.Builder<IN, OUT, OP> transformer(Operator<IN, OUT> operator) {
                operation.fallback(operator);
                return new SafeOperation.Builder<>(operation);
            }
        }
    }

    public static class SafeOperation<IN, OUT, OP extends Operator<IN, OUT>> {
        public static class Builder<IN, OUT, OP extends Operator<IN, OUT>> {
            private final WorkStation.Builder<IN, OUT, OP> operation;

            public Builder(WorkStation.Builder<IN, OUT, OP> operation) {
                this.operation = operation;
            }

            public Builder<IN, OUT, OP> onError(BaseError.SafeError<IN> onError) {
                operation.onError(onError);
                return this;
            }

            public Builder<IN, OUT, OP> onError(BaseError.UnSafeError<IN> onError) {
                operation.onError(onError);
                return this;
            }

            /**
             * Adds a pre-processor skip rule evaluated before station preparation.
             */
            public Builder<IN, OUT, OP> skipIf(StationSkipTest predicate) {
                operation.skipIf(predicate);
                return this;
            }

            /**
             * Adds a post-processor skip rule evaluated after parameter resolution and
             * side-compute waits.
             */
            public Builder<IN, OUT, OP> skipIfPost(StationSkipTest predicate) {
                operation.skipIfPost(predicate);
                return this;
            }

            public WorkStation.Builder<IN, OUT, OP> transformer(Operator<IN, OUT> operator) {
                operation.fallback(operator);
                return operation;
            }

            public WorkStation<IN, OUT> build() {
                return operation.build();
            }
        }
    }

    private record MetadataEntry<T>(Class<T> type, T value) {}

    private static StationMetadata buildMetadata(List<MetadataEntry<?>> metadata) {
        if (metadata.isEmpty()) {
            return StationMetadata.empty();
        }
        MutableStationMetadata mutable = new MutableStationMetadata();
        metadata.forEach(entry -> putMetadata(mutable, entry));
        return mutable.immutableCopy();
    }

    private static <T> void putMetadata(MutableStationMetadata metadata, MetadataEntry<T> entry) {
        metadata.put(entry.type(), entry.value());
    }
}
