package io.github.gear4jtest.core.processor;

import io.github.gear4jtest.core.context.StepExecution;
import io.github.gear4jtest.core.model.refactor.ProcessingOperationDataModel;

@FunctionalInterface
public interface CustomProcessingOperationProcessor<T> extends BaseProcessor<ProcessingOperationDataModel, T, StepExecution> {

	default boolean isApplicable(ProcessingOperationDataModel model, T customModel) {
		return true;
	}

}
