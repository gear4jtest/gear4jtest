package io.github.gear4jtest.core.api.context;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class ResolvedParameters {
    private final Map<StationParameterModel<?, ?>, Object> resolved = new ConcurrentHashMap<>();

    public boolean has(StationParameterModel<?, ?> model) {
        return resolved.containsKey(model);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(StationParameterModel<?, ?> model) {
        return (T) resolved.get(model);
    }

    public <T> Resolution<T> resolve(StationParameterModel<?, ?> rawModel,
                                     ParameterResolutionContext<?> interpretationCtx) {
        Objects.requireNonNull(rawModel, "rawModel");
        Objects.requireNonNull(interpretationCtx, "interpretationCtx");

        final boolean[] computedHere = { false };

        @SuppressWarnings("unchecked")
        T value = (T) resolved.computeIfAbsent(rawModel, model -> {
            computedHere[0] = true;
            return ((StationParameterModel<?, T>) model).getValue(interpretationCtx);
        });

        return new Resolution<>(value, !computedHere[0]);
    }

    public <T> T resolveIfAbsent(StationParameterModel<?, ?> rawModel,
                                 ParameterResolutionContext<?> interpretationCtx) {
        return this.<T>resolve(rawModel, interpretationCtx).value();
    }

    public record Resolution<T>(T value, boolean cacheHit) {}
}
