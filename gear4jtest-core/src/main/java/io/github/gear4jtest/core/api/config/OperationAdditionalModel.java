package io.github.gear4jtest.core.api.config;

import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.station.WorkStation;

public interface OperationAdditionalModel<IN, OUT, OP extends Operator<IN, OUT>> {
    void contributeTo(WorkStation.Builder<IN, OUT, OP> builder);
}
