package io.github.gear4jtest.core.model.refactor;

import io.github.gear4jtest.core.model.refactor.ExecutionContext;
import io.github.gear4jtest.core.model.refactor.OperationDefinition;
import io.github.gear4jtest.core.model.refactor.OperationExecution;

@FunctionalInterface
public interface Processor {

	void process(Object input, ExecutionContext context, OperationDefinition<?, ?> model, OperationExecution operationExecution) throws Exception;
}
