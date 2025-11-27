package io.github.gear4jtest.core.model;

import io.github.gear4jtest.core.model.refactor.ProcessingOperationDefinition;
import io.github.gear4jtest.core.model.refactor.Transformer;

public interface OperationAdditionalModel<IN, OUT, OP extends Transformer<IN, OUT>> {

    void contributeTo(ProcessingOperationDefinition.Builder<IN, OUT, OP> builder);
}
