package io.github.gear4jtest.core.engine.support;

import java.util.ArrayList;
import java.util.List;

import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.behavior.Processor;
import io.github.gear4jtest.core.api.context.ParameterResolutionContext;
import io.github.gear4jtest.core.api.context.ResolvedParameters;
import io.github.gear4jtest.core.api.context.StationContextUtils;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.context.StationParameter;
import io.github.gear4jtest.core.api.context.StationParameterModel;
import io.github.gear4jtest.core.engine.context.EngineStationContexts;
import io.github.gear4jtest.core.event.ParameterResolvedEvent;

public class WorkerParamsInjector implements Processor {
    @Override
    public FailureMode beforeExecutionFailureMode() {
        return FailureMode.FAIL_STATION;
    }

    @Override
    public <I> void beforeExecution(I input, StationExecutionContext operationExecution) {
        var processingParameters = StationContextUtils.getProcessingParameters(operationExecution);
        var transformer = StationContextUtils.getTransformer(operationExecution);
        if (processingParameters.isEmpty() || transformer.isEmpty()) {
            return;
        }

        ParameterResolutionContext<I> ctx = new ParameterResolutionContext<>(input,
                operationExecution.getGlobalContext(),
                operationExecution);
        InjectedParameters injectedParameters = new InjectedParameters();
        EngineStationContexts.addCapability(operationExecution, InjectedParameters.class, injectedParameters);

        for (StationParameterModel<?, ?> rawParam : processingParameters.get().getParameters()) {
            injectParameter(rawParam, transformer.get(), ctx, operationExecution, injectedParameters);
        }
    }

    @SuppressWarnings("unchecked")
    private <IN, OUT, OP extends Operator<IN, OUT>, T> void injectParameter(StationParameterModel<?, ?> rawParam,
                                                                            Operator<?, ?> rawOperator,
                                                                            ParameterResolutionContext<?> ctx,
                                                                            StationExecutionContext operationExecution,
                                                                            InjectedParameters injectedParameters) {
        StationParameterModel<OP, T> param = (StationParameterModel<OP, T>) rawParam;
        OP op = (OP) rawOperator;

        StationParameter<T> parameterValue = param.getParamRetriever().getParameterValue(op);

        if (parameterValue == null) {
            return;
        }

        ResolvedParameters cache = operationExecution.getResolvedParameters();
        ResolvedParameters.Resolution<T> resolution = cache.resolve(param, ctx);
        T value = resolution.value();
        parameterValue.injectValue(value);
        injectedParameters.record(parameterValue);

        publishParameterResolvedEvent(rawParam, operationExecution, resolution, value);
    }

    private void publishParameterResolvedEvent(StationParameterModel<?, ?> rawParam,
                                               StationExecutionContext operationExecution,
                                               ResolvedParameters.Resolution<?> resolution,
                                               Object value) {
        if (!operationExecution.getGlobalContext().getEventRuntimeOptions().isParameterResolvedEventsEnabled()) {
            return;
        }

        if (operationExecution.getServices().getEventManager() == null) {
            return;
        }

        operationExecution.getServices().getEventManager()
                .publish(new ParameterResolvedEvent(operationExecution.getGlobalContext().getAssemblyLineId(),
                        operationExecution.getGlobalContext().getExecutionId(),
                        EngineStationContexts.trace(operationExecution).getId(),
                        operationExecution.getOperationId(),
                        EngineStationContexts.trace(operationExecution).getParentOperationId(),
                        EngineStationContexts.trace(operationExecution).getItemId(), rawParam.describe(),
                        resolution.cacheHit(),
                        value != null ? value.getClass().getName() : null));
    }

    @Override
    public void afterExecution(Object result, StationExecutionContext operationExecution) {
        operationExecution.getCapability(InjectedParameters.class).ifPresent(InjectedParameters::cleanup);
    }

    private static final class InjectedParameters {
        private final List<StationParameter<?>> parameters = new ArrayList<>();

        private void record(StationParameter<?> parameter) {
            parameters.add(parameter);
        }

        private void cleanup() {
            for (int index = parameters.size() - 1; index >= 0; index--) {
                parameters.get(index).afterExecutionCleanup();
            }
            parameters.clear();
        }
    }
}
