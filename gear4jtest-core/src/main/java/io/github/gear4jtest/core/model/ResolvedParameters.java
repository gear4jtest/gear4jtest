package io.github.gear4jtest.core.model;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class ResolvedParameters {

    private final Map<WorkerParamsInjector.ParameterModel<?, ?>, Object> resolved = new ConcurrentHashMap<>();

    public boolean has(WorkerParamsInjector.ParameterModel<?, ?> model) {
        return resolved.containsKey(model);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(WorkerParamsInjector.ParameterModel<?, ?> model) {
        return (T) resolved.get(model);
    }

    @SuppressWarnings("unchecked")
    public <T> T resolveIfAbsent(WorkerParamsInjector.ParameterModel<?, ?> rawModel,
                                 WorkerParamsInjector.InterpretationContext<?> interpretationCtx) {
        Objects.requireNonNull(rawModel, "model");
        Objects.requireNonNull(interpretationCtx, "interpretationCtx");

        Object value =
                resolved.computeIfAbsent(rawModel,
                        m -> ((WorkerParamsInjector.ParameterModel<?, T>) m).getValue(interpretationCtx));

        return (T) value;
    }
}
