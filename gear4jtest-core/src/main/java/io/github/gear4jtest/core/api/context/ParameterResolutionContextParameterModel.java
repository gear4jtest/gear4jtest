package io.github.gear4jtest.core.api.context;

import java.util.function.Function;

import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.station.WorkStation;

/**
 * Parameter model backed by a resolver using the current parameter resolution
 * context.
 */
public final class ParameterResolutionContextParameterModel<IN, OP extends Operator<?, ?>, T>
        extends StationParameterModel<OP, T> {
    private final Function<ParameterResolutionContext<IN>, T> resolver;

    public ParameterResolutionContextParameterModel(WorkStation.ParamRetriever<OP, T> paramRetriever,
                                                    Function<ParameterResolutionContext<IN>, T> resolver) {
        super(paramRetriever);
        this.resolver = resolver;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T getValue(ParameterResolutionContext<?> ctx) {
        return resolver.apply((ParameterResolutionContext<IN>) ctx);
    }
}
