package io.github.gear4jtest.core.api.station;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import io.github.gear4jtest.core.api.behavior.BaseError;
import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.behavior.Processor;
import io.github.gear4jtest.core.api.behavior.StationSkipTest;
import io.github.gear4jtest.core.api.config.OperationAdditionalModel;
import io.github.gear4jtest.core.api.config.StationConfigurationDefinition;
import io.github.gear4jtest.core.engine.support.WorkerParamsInjector;

public class WorkStation<IN, OUT> extends AbstractStation<IN, OUT> {
    protected Class<Operator<IN, OUT>> type;
    protected List<WorkerParamsInjector.ParameterModel<?, ?>> parameters;
    /**
     * Whether the operator instance is reused for the whole pipeline run.
     */
    protected boolean reuseOperatorInstanceWithinRun = false;
    private StationConfigurationDefinition operationConfiguration;

    WorkStation() {
        super("", StationKind.PROCESSING);
    }

    public List<WorkerParamsInjector.ParameterModel<?, ?>> getParameters() {
        return parameters;
    }

    public void setParameters(List<WorkerParamsInjector.ParameterModel<?, ?>> parameters) {
        this.parameters = parameters;
    }

    public StationConfigurationDefinition getOperationConfiguration() {
        return operationConfiguration;
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
        private final WorkStation<IN, OUT> managedInstance;

        public Builder() {
            managedInstance = new WorkStation<>();
        }

        @SuppressWarnings({ "unchecked", "rawtypes" })
        public <A, T extends Operator<IN, A>> Builder<IN, A, T> type(Class<T> type) {
            managedInstance.type = (Class) type;
            return (Builder<IN, A, T>) this;
        }

        public Builder<IN, OUT, OP> id(String id) {
            managedInstance.id = id;
            return this;
        }

        public Builder<IN, OUT, OP> reuseOperatorInstanceWithinRun() {
            managedInstance.reuseOperatorInstanceWithinRun = true;
            return this;
        }

        public Builder<IN, OUT, OP> reuseOperatorInstanceWithinRun(boolean enabled) {
            managedInstance.reuseOperatorInstanceWithinRun = enabled;
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
            if (managedInstance.processors == null) {
                managedInstance.processors = new ArrayList<>();
            }
            if (managedInstance.processors.stream().noneMatch(p -> p instanceof WorkerParamsInjector)) {
                managedInstance.processors.add(new WorkerParamsInjector());
            }
        }

        private void addParameter(WorkerParamsInjector.ParameterModel<OP, ?> parameterModel) {
            if (managedInstance.parameters == null) {
                managedInstance.parameters = new ArrayList<>();
            }
            managedInstance.parameters.add(parameterModel);
        }

        public Builder<IN, OUT, OP> addProcessor(Processor processor) {
            if (this.managedInstance.processors == null) {
                this.managedInstance.processors = new ArrayList<>();
            }
            this.managedInstance.processors.add(processor);
            return this;
        }

        public Builder<IN, OUT, OP> onError(BaseError.SafeError<IN> onError) {
            if (this.managedInstance.onErrors == null) {
                this.managedInstance.onErrors = new ArrayList<>();
            }
            this.managedInstance.onErrors.add(onError);
            return this;
        }

        public UnsafeOperation.Builder<IN, OUT, OP> onError(BaseError.UnSafeError<IN> onError) {
            if (this.managedInstance.onErrors == null) {
                this.managedInstance.onErrors = new ArrayList<>();
            }
            this.managedInstance.onErrors.add(onError);
            return new UnsafeOperation.Builder<>(this);
        }

        public Builder<IN, OUT, OP> fallback(Operator<IN, OUT> operator) {
            this.managedInstance.fallbackOperator = operator;
            return this;
        }

        /**
         * Adds a pre-processor skip rule evaluated before station preparation.
         */
        public UnsafeOperation.Builder<IN, OUT, OP> skipIf(StationSkipTest predicate) {
            managedInstance.skipIf(predicate);
            return new UnsafeOperation.Builder<>(this);
        }

        /**
         * Adds a post-processor skip rule evaluated after parameter resolution and
         * side-compute waits.
         */
        public UnsafeOperation.Builder<IN, OUT, OP> skipIfPost(StationSkipTest predicate) {
            managedInstance.skipIfPost(predicate);
            return new UnsafeOperation.Builder<>(this);
        }

        public <T> Builder<IN, OUT, OP> metadata(Class<T> type, T value) {
            managedInstance.putMetadata(type, value);
            return this;
        }

        public Builder<IN, OUT, OP> additionalModel(OperationAdditionalModel<IN, OUT, OP> model) {
            model.contributeTo(this);
            return this;
        }

        public WorkStation<IN, OUT> build() {
            return managedInstance;
        }
    }

    public static class UnsafeOperation<IN, OUT, OP extends Operator<IN, OUT>> {
        private WorkStation.Builder<IN, OUT, OP> operation;

        public static class Builder<IN, OUT, OP extends Operator<IN, OUT>> {
            private UnsafeOperation<IN, OUT, OP> managedInstance;

            public Builder(WorkStation.Builder<IN, OUT, OP> operation) {
                this.managedInstance = new UnsafeOperation<>();
                this.managedInstance.operation = operation;
            }

            public Builder<IN, OUT, OP> onError(BaseError.SafeError<IN> onError) {
                this.managedInstance.operation.onError(onError);
                return this;
            }

            public UnsafeOperation.Builder<IN, OUT, OP> onError(BaseError.UnSafeError<IN> onError) {
                this.managedInstance.operation.onError(onError);
                return this;
            }

            /**
             * Adds a pre-processor skip rule evaluated before station preparation.
             */
            public UnsafeOperation.Builder<IN, OUT, OP> skipIf(StationSkipTest predicate) {
                managedInstance.operation.skipIf(predicate);
                return this;
            }

            /**
             * Adds a post-processor skip rule evaluated after parameter resolution and
             * side-compute waits.
             */
            public UnsafeOperation.Builder<IN, OUT, OP> skipIfPost(StationSkipTest predicate) {
                managedInstance.operation.skipIfPost(predicate);
                return this;
            }

            public SafeOperation.Builder<IN, OUT, OP> transformer(Operator<IN, OUT> operator) {
                this.managedInstance.operation.fallback(operator);
                return new SafeOperation.Builder<>(this.managedInstance.operation);
            }
        }
    }

    public static class SafeOperation<IN, OUT, OP extends Operator<IN, OUT>> {
        private WorkStation.Builder<IN, OUT, OP> operation;

        public static class Builder<IN, OUT, OP extends Operator<IN, OUT>> {
            private SafeOperation<IN, OUT, OP> managedInstance;

            public Builder(WorkStation.Builder<IN, OUT, OP> operation) {
                this.managedInstance = new SafeOperation<>();
                this.managedInstance.operation = operation;
            }

            public Builder<IN, OUT, OP> onError(BaseError.SafeError<IN> onError) {
                this.managedInstance.operation.onError(onError);
                return this;
            }

            public Builder<IN, OUT, OP> onError(BaseError.UnSafeError<IN> onError) {
                this.managedInstance.operation.onError(onError);
                return this;
            }

            /**
             * Adds a pre-processor skip rule evaluated before station preparation.
             */
            public Builder<IN, OUT, OP> skipIf(StationSkipTest predicate) {
                managedInstance.operation.skipIf(predicate);
                return this;
            }

            /**
             * Adds a post-processor skip rule evaluated after parameter resolution and
             * side-compute waits.
             */
            public Builder<IN, OUT, OP> skipIfPost(StationSkipTest predicate) {
                managedInstance.operation.skipIfPost(predicate);
                return this;
            }

            public WorkStation.Builder<IN, OUT, OP> transformer(Operator<IN, OUT> operator) {
                this.managedInstance.operation.fallback(operator);
                return this.managedInstance.operation;
            }

            public WorkStation<IN, OUT> build() {
                return this.managedInstance.operation.build();
            }
        }
    }
}
