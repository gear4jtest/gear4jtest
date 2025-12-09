package io.github.gear4jtest.core.model.refactor;

import io.github.gear4jtest.core.persistence.OperationExecutionRecord;

public interface OperationDefinition<IN, OUT> {

    OperationExecutionRecord run(IN input, ExecutionContext context);
}
