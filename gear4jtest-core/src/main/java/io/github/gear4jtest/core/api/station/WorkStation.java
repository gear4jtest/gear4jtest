package io.github.gear4jtest.core.api.station;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import io.github.gear4jtest.core.api.behavior.BaseError;
import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.behavior.Processor;
import io.github.gear4jtest.core.api.behavior.StationSkipTest;
import io.github.gear4jtest.core.api.behavior.StationSkipper;
import io.github.gear4jtest.core.api.config.OperationAdditionalModel;
import io.github.gear4jtest.core.engine.support.WorkerParamsInjector;

public class WorkStation<IN, OUT> extends AbstractStation<IN, OUT> {
    protected Class<Operator<IN, OUT>> type;
    protected List<WorkerParamsInjector.ParameterModel<?, ?>> parameters;
    /**
     * Whether the operator instance is reused for the whole pipeline run.
     */
    protected boolean reuseOperatorInstanceWithinRun = false;

    WorkStation() {
        super("", StationKind.PROCESSING);
    }

    public List<WorkerParamsInjector.ParameterModel<?, ?>> getParameters() {
        return parameters;
    }

    public void setParameters(List<WorkerParamsInjector.ParameterModel<?, ?>> parameters) {
        this.parameters = parameters;
    }

    public Class<Operator<IN, OUT>> getType() {
        return type;
    }

    public boolean isReuseOperatorInstanceWithinRun() {
        return reuseOperatorInstanceWithinRun;
    }

    @FunctionalInterface
    public interface ParamRetriever<T extends Operator<?, ?>, U> {
        WorkerParamsInjector.Parameter<U> getParameterValue(T operation);
    }

    public static class Builder<IN, OUT, OP extends Operator<IN, OUT>> {
        private String id = "";
        private Class<? extends Operator<?, ?>> type;
        private final List<WorkerParamsInjector.ParameterModel<?, ?>> parameters;
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
            this.parameters = source.parameters;
            this.processors = source.processors;
            this.onErrors = source.onErrors;
            this.skippers = source.skippers;
            this.metadata = source.metadata;
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

            addParameterInjectorIfNecessary();
            addParameter(new WorkerParamsInjector.InterpretationContextParameterModel<>(retriever, ctx -> value));
            return this;
        }

        /**
         * Binds an operator parameter to a context-independent supplier.
         */
        public <A> Builder<IN, OUT, OP> parameter(ParamRetriever<OP, A> retriever,
                                                  java.util.function.Supplier<A> supplier) {

            addParameterInjectorIfNecessary();
            addParameter(new WorkerParamsInjector.InterpretationContextParameterModel<>(retriever,
                    ctx -> supplier.get()));
            return this;
        }

        /**
         * Binds an operator parameter to a resolver that can inspect the current input
         * and execution context.
         */
        public <A> Builder<IN, OUT, OP> parameter(ParamRetriever<OP, A> retriever,
                                                  Function<WorkerParamsInjector.InterpretationContext<IN>, A> resolver) {

            addParameterInjectorIfNecessary();
            addParameter(new WorkerParamsInjector.InterpretationContextParameterModel<>(retriever, resolver));
            return this;
        }

        private void addParameterInjectorIfNecessary() {
            if (processors.stream().noneMatch(p -> p instanceof WorkerParamsInjector)) {
                processors.add(new WorkerParamsInjector());
            }
        }

        private void addParameter(WorkerParamsInjector.ParameterModel<OP, ?> parameterModel) {
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

        public WorkStation<IN, OUT> build() {
            WorkStation<IN, OUT> station = new WorkStation<>();
            applyBuilder(station, this);
            return station;
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

    @SuppressWarnings({ "unchecked", "rawtypes" })
    static <IN, OUT, OP extends Operator<IN, OUT>> void applyBuilder(WorkStation<IN, OUT> station,
                                                                     Builder<IN, OUT, OP> builder) {
        station.id = builder.id;
        station.type = (Class) builder.type;
        station.processors = builder.processors.isEmpty() ? null : new ArrayList<>(builder.processors);
        station.parameters = builder.parameters.isEmpty() ? null : new ArrayList<>(builder.parameters);
        station.onErrors = builder.onErrors.isEmpty() ? null : new ArrayList<>(builder.onErrors);
        station.fallbackOperator = (Operator<IN, OUT>) builder.fallbackOperator;
        station.reuseOperatorInstanceWithinRun = builder.reuseOperatorInstanceWithinRun;
        builder.skippers.forEach(station::addSkipper);
        builder.metadata.forEach(entry -> applyMetadata(station, entry));
    }

    private static <IN, OUT, T> void applyMetadata(WorkStation<IN, OUT> station, MetadataEntry<T> entry) {
        station.putMetadata(entry.type(), entry.value());
    }
}
