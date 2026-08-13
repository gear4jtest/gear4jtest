package io.github.gear4jtest.core.api.context;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import io.github.gear4jtest.core.api.behavior.Operator;

public final class ResolvedParameters {
    private final Map<StationParameterModel<?, ?>, Object> resolved = new ConcurrentHashMap<>();

    public boolean has(StationParameterModel<?, ?> model) {
        return resolved.containsKey(model);
    }

    public <OP extends Operator<?, ?>, T> T get(StationParameterModel<OP, T> model) {
        return valueAs(resolved.get(model));
    }

    public <OP extends Operator<?, ?>, T> Resolution<T> resolve(StationParameterModel<OP, T> model,
                                                                ParameterResolutionContext<?> interpretationCtx) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(interpretationCtx, "interpretationCtx");

        final boolean[] computedHere = { false };

        Object resolvedValue = resolved.computeIfAbsent(model, ignored -> {
            computedHere[0] = true;
            return model.getValue(interpretationCtx);
        });

        return new Resolution<>(valueAs(resolvedValue), !computedHere[0]);
    }

    public <OP extends Operator<?, ?>, T> T resolveIfAbsent(StationParameterModel<OP, T> model,
                                                            ParameterResolutionContext<?> interpretationCtx) {
        return resolve(model, interpretationCtx).value();
    }

    @SuppressWarnings("unchecked")
    private static <T> T valueAs(Object value) {
        return (T) value;
    }

    public record Resolution<T>(T value, boolean cacheHit) {}
}
