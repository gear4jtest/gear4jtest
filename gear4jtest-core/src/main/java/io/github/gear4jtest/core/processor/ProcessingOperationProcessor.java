package io.github.gear4jtest.core.processor;

import io.github.gear4jtest.core.context.StepExecution;
import io.github.gear4jtest.core.model.refactor.ProcessingOperationDataModel;

@FunctionalInterface
public interface ProcessingOperationProcessor extends BaseProcessor<ProcessingOperationDataModel, Void, StepExecution> {
	
	default boolean isApplicable(ProcessingOperationDataModel model) {
		return true;
	}
	
}
