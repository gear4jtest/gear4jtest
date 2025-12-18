package io.github.gear4jtest.core.model;

public interface OperationAdditionalModel<IN, OUT, OP extends Operator<IN, OUT>> {

    void contributeTo(WorkStation.Builder<IN, OUT, OP> builder);
}
