package io.github.gear4jtest.core.api.context;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import io.github.gear4jtest.core.engine.support.WorkerParamsInjector;

public final class ResolvedParameters {
    private final Map<WorkerParamsInjector.ParameterModel<?, ?>, Object> resolved = new ConcurrentHashMap<>();

    public boolean has(WorkerParamsInjector.ParameterModel<?, ?> model) {
        return resolved.containsKey(model);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(WorkerParamsInjector.ParameterModel<?, ?> model) {
        return (T) resolved.get(model);
    }

    public <T> Resolution<T> resolve(WorkerParamsInjector.ParameterModel<?, ?> rawModel,
                                     WorkerParamsInjector.InterpretationContext<?> interpretationCtx) {
        Objects.requireNonNull(rawModel, "rawModel");
        Objects.requireNonNull(interpretationCtx, "interpretationCtx");

        final boolean[] computedHere = { false };

        @SuppressWarnings("unchecked")
        T value = (T) resolved.computeIfAbsent(rawModel, model -> {
            computedHere[0] = true;
            return ((WorkerParamsInjector.ParameterModel<?, T>) model).getValue(interpretationCtx);
        });

        return new Resolution<>(value, !computedHere[0]);
    }

    public <T> T resolveIfAbsent(WorkerParamsInjector.ParameterModel<?, ?> rawModel,
                                 WorkerParamsInjector.InterpretationContext<?> interpretationCtx) {
        return this.<T>resolve(rawModel, interpretationCtx).value();
    }

    public record Resolution<T>(T value, boolean cacheHit) {}
}
