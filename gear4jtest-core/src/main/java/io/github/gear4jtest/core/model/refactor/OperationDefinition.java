package io.github.gear4jtest.core.model.refactor;

public interface OperationDefinition<IN, OUT> {

    OperationResult<OUT> run(IN input, ExecutionContext context);
}
