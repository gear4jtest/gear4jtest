package io.github.gear4jtest.core.api.context;

import io.github.gear4jtest.core.engine.support.WorkerParamsInjector;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class ResolvedParameters {

    public record Resolution<T>(T value, boolean cacheHit) {
    }

    private final Map<WorkerParamsInjector.ParameterModel<?, ?>, Object> resolved = new ConcurrentHashMap<>();

    public boolean has(WorkerParamsInjector.ParameterModel<?, ?> model) {
        return resolved.containsKey(model);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(WorkerParamsInjector.ParameterModel<?, ?> model) {
        return (T) resolved.get(model);
    }

    public <T> Resolution<T> resolve(
            WorkerParamsInjector.ParameterModel<?, ?> rawModel,
            WorkerParamsInjector.InterpretationContext<?> interpretationCtx) {
        Objects.requireNonNull(rawModel, "model");
        Objects.requireNonNull(interpretationCtx, "interpretationCtx");

        if (resolved.containsKey(rawModel)) {
            return new Resolution<>(get(rawModel), true);
        }

        @SuppressWarnings("unchecked")
        T value = (T) resolved.computeIfAbsent(
                rawModel,
                model -> ((WorkerParamsInjector.ParameterModel<?, T>) model).getValue(interpretationCtx));
        return new Resolution<>(value, false);
    }

    public <T> T resolveIfAbsent(
            WorkerParamsInjector.ParameterModel<?, ?> rawModel,
            WorkerParamsInjector.InterpretationContext<?> interpretationCtx) {
        return this.<T>resolve(rawModel, interpretationCtx).value();
    }
}
