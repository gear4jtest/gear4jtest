package io.github.gear4jtest.core.api.context;

import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.station.WorkStation;

/**
 * Definition of a value injected into an operator {@link StationParameter}.
 */
public abstract class StationParameterModel<OP extends Operator<?, ?>, T> {
    private final WorkStation.ParamRetriever<OP, T> paramRetriever;

    protected StationParameterModel(WorkStation.ParamRetriever<OP, T> paramRetriever) {
        this.paramRetriever = paramRetriever;
    }

    public WorkStation.ParamRetriever<OP, T> getParamRetriever() {
        return paramRetriever;
    }

    public String describe() {
        return getClass().getName() + '@' + Integer.toHexString(System.identityHashCode(this));
    }

    public abstract T getValue(ParameterResolutionContext<?> ctx);
}
