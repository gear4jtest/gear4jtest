package io.github.gear4jtest.core.engine.support;

import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.behavior.Processor;
import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.ResolvedParameters;
import io.github.gear4jtest.core.api.context.StationContextUtils;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.WorkStation;
import io.github.gear4jtest.core.event.ParameterResolvedEvent;
import io.github.gear4jtest.core.sidecompute.DefaultSideComputeAccessor;
import io.github.gear4jtest.core.sidecompute.SideComputeAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class WorkerParamsInjector implements Processor {

    @Override
    public FailureMode beforeExecutionFailureMode() {
        return FailureMode.FAIL_STATION;
    }

    @Override
    public <I> void beforeExecution(I input, StationExecutionContext operationExecution) {
        var processingParameters = StationContextUtils.getProcessingParameters(operationExecution);
        var transformer = StationContextUtils.getRawTransformer(operationExecution);
        if (processingParameters.isEmpty() || transformer.isEmpty()) {
            return;
        }

        InterpretationContext<I> ctx =
                new InterpretationContext<>(input, operationExecution.getGlobalContext(), operationExecution);

        for (ParameterModel<?, ?> rawParam : processingParameters.get().getParameters()) {
            injectParameter(rawParam, transformer.get(), ctx, operationExecution);
        }
    }

    @SuppressWarnings("unchecked")
    private <IN, OUT, OP extends Operator<IN, OUT>, T> void injectParameter(
            ParameterModel<?, ?> rawParam,
            Operator<?, ?> rawOperator,
            InterpretationContext<?> ctx,
            StationExecutionContext operationExecution) {
        ParameterModel<OP, T> param = (ParameterModel<OP, T>) rawParam;
        OP op = (OP) rawOperator;

        WorkerParamsInjector.Parameter<T> parameterValue =
                param.getParamRetriever().getParameterValue(op);

        if (parameterValue == null) {
            return;
        }

        ResolvedParameters cache = operationExecution.getResolvedParameters();
        ResolvedParameters.Resolution<T> resolution = cache.resolve(rawParam, ctx);
        T value = resolution.value();
        parameterValue.injectValue(value);

        publishParameterResolvedEvent(rawParam, operationExecution, resolution, value);
    }

    private void publishParameterResolvedEvent(
            ParameterModel<?, ?> rawParam,
            StationExecutionContext operationExecution,
            ResolvedParameters.Resolution<?> resolution,
            Object value) {
        if (!operationExecution.getGlobalContext()
                .getEventRuntimeOptions()
                .isParameterResolvedEventsEnabled()) {
            return;
        }

        if (operationExecution.getServices().getEventManager() == null) {
            return;
        }

        operationExecution.getServices().getEventManager().publish(new ParameterResolvedEvent(
                operationExecution.getGlobalContext().getPipelineId(),
                operationExecution.getGlobalContext().getExecutionId(),
                operationExecution.getRecord().getId(),
                operationExecution.getOperationId(),
                operationExecution.getRecord().getParentOperationId(),
                operationExecution.getRecord().getItemId(),
                rawParam.describe(),
                resolution.cacheHit(),
                value != null ? value.getClass().getName() : null));
    }

    @Override
    public void afterExecution(Object result, StationExecutionContext operationExecution) {
        var processingParameters = StationContextUtils.getProcessingParameters(operationExecution);
        var transformer = StationContextUtils.getRawTransformer(operationExecution);
        if (processingParameters.isEmpty() || transformer.isEmpty()) {
            return;
        }

        for (ParameterModel<?, ?> rawParam : processingParameters.get().getParameters()) {
            cleanupParameter(rawParam, transformer.get());
        }
    }

    @SuppressWarnings("unchecked")
    private <IN, OUT, OP extends Operator<IN, OUT>, T> void cleanupParameter(
            ParameterModel<?, ?> rawParam,
            Operator<?, ?> rawOperator) {

        ParameterModel<OP, T> paramModel = (ParameterModel<OP, T>) rawParam;
        OP op = (OP) rawOperator;

        WorkerParamsInjector.Parameter<T> parameterValue =
                paramModel.getParamRetriever().getParameterValue(op);

        if (parameterValue != null) {
            parameterValue.afterExecutionCleanup();
        }
    }

    public static class Parameters {

        private final List<ParameterModel<?, ?>> parameters;

        public Parameters() {
            this.parameters = new ArrayList<>();
        }

        public boolean hasParameters() {
            return !this.parameters.isEmpty();
        }

        public List<ParameterModel<?, ?>> getParameters() {
            return parameters;
        }

        public static Builder newBuilder() {
            return new Builder();
        }

        public static class Builder {

            private final Parameters instance = new Parameters();

            public <OP extends Operator<?, ?>, T> Builder withParameter(ParameterModel parameter) {
                instance.parameters.add(parameter);
                return this;
            }

            public Builder withParameters(Optional<Parameters> parameters) {
                parameters.ifPresent(p -> instance.parameters.addAll(p.parameters));
                return this;
            }

            public Parameters build() {
                return instance;
            }
        }
    }

    public static class Parameter<T> {

        public enum LifecyclePolicy {
            PERSISTENT,
            PER_EXECUTION
        }

        private final LifecyclePolicy lifecyclePolicy;
        private final T defaultValue;
        private T value;

        private Parameter(Builder<T> builder) {
            this.lifecyclePolicy = builder.lifecyclePolicy;
            this.defaultValue = builder.defaultValue;
            this.value = builder.defaultValue;
        }

        public static <T> Builder<T> newBuilder() {
            return new Builder<>();
        }

        public static class Builder<T> {

            private LifecyclePolicy lifecyclePolicy = LifecyclePolicy.PERSISTENT;
            private T defaultValue;

            public Builder<T> lifecyclePolicy(LifecyclePolicy lifecyclePolicy) {
                this.lifecyclePolicy = lifecyclePolicy;
                return this;
            }

            public Builder<T> defaultValue(T defaultValue) {
                this.defaultValue = defaultValue;
                return this;
            }

            public Parameter<T> build() {
                return new Parameter<>(this);
            }
        }

        public T getValue() {
            return value;
        }

        void injectValue(T newValue) {
            this.value = newValue;
        }

        void afterExecutionCleanup() {
            if (lifecyclePolicy == LifecyclePolicy.PER_EXECUTION) {
                this.value = defaultValue;
            }
        }
    }

    public static abstract class ParameterModel<OP extends Operator<?, ?>, T> {

        private final WorkStation.ParamRetriever<OP, T> paramRetriever;

        protected ParameterModel(WorkStation.ParamRetriever<OP, T> paramRetriever) {
            this.paramRetriever = paramRetriever;
        }

        public WorkStation.ParamRetriever<OP, T> getParamRetriever() {
            return paramRetriever;
        }

        public String describe() {
            return getClass().getName() + '@' + Integer.toHexString(System.identityHashCode(this));
        }

        public abstract T getValue(InterpretationContext<?> ctx);
    }

    public static class InterpretationContextParameterModel<IN, OP extends Operator<?, ?>, T>
            extends ParameterModel<OP, T> {

        private final Function<InterpretationContext<IN>, T> resolver;

        public InterpretationContextParameterModel(
                WorkStation.ParamRetriever<OP, T> paramRetriever,
                Function<InterpretationContext<IN>, T> resolver) {
            super(paramRetriever);
            this.resolver = resolver;
        }

        @Override
        @SuppressWarnings("unchecked")
        public T getValue(InterpretationContext<?> ctx) {
            return resolver.apply((InterpretationContext<IN>) ctx);
        }
    }

    public static final class InterpretationContext<IN> {

        private final IN item;
        private final ExecutionContext executionContext;
        private final StationExecutionContext stationExecutionContext;
        private final SideComputeAccessor sideComputeAccessor;

        public InterpretationContext(
                IN item,
                ExecutionContext executionContext,
                StationExecutionContext stationExecutionContext) {
            this.item = item;
            this.executionContext = executionContext;
            this.stationExecutionContext = stationExecutionContext;
            this.sideComputeAccessor = stationExecutionContext
                    .getCapability(SideComputeAccessor.class)
                    .orElseGet(() -> new DefaultSideComputeAccessor(executionContext));
        }

        public IN getItem() {
            return item;
        }

        public ExecutionContext getExecutionContext() {
            return executionContext;
        }

        public StationExecutionContext getOperationExecutionContext() {
            return stationExecutionContext;
        }

        public SideComputeAccessor getSideCompute() {
            return sideComputeAccessor;
        }
    }
}
