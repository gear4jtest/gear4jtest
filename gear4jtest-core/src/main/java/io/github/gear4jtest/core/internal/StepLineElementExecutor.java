package io.github.gear4jtest.core.internal;

import io.github.gear4jtest.core.context.StepExecution;
import io.github.gear4jtest.core.model.Operation;
import io.github.gear4jtest.core.processor.ProcessorChain;
import io.github.gear4jtest.core.processor.ProcessorChainResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class StepLineElementExecutor {
	private static final Logger LOGGER = LogManager.getLogger("StepLineElementExecutor");

	private final StepLineElement stepLineElement;
	private final StepLineElementOperationInitiator stepLineElementOperationInitiator;

	public StepLineElementExecutor(StepLineElement stepLineElement) {
		this.stepLineElement = stepLineElement;
		this.stepLineElementOperationInitiator = new StepLineElementOperationInitiator(stepLineElement);
	}

	// Should itemexecution contains item ?
	public StepExecution execute(StepExecution execution) {
		try {
			Operation<?, ?> operation = stepLineElementOperationInitiator.initiate();

			execution.withOperation(operation);
			ProcessorChain chain = new ProcessorChain(this.stepLineElement.getProcessorChain(), execution.getItem(), execution, operation);
			ProcessorChainResult result = chain.processChain();
			if (!result.isProcessed()) {
				if (this.stepLineElement.getTransformer() == null) {
					throw new IllegalStateException("No transformer specified whereas input has not been processed");
				} else {
					execution.getItem().updateItem(this.stepLineElement.getTransformer().tranform(execution.getItem().getItem()));
				}
			} else {
				execution.getItem().updateItem(result.getOutput());
			}
		} catch (Exception e) {
			LOGGER.error("Error while executing operation", e);
			if (this.stepLineElement.getTransformer() == null
					|| !this.stepLineElement.isIgnoreOperationFactoryException()) {
				throw buildRuntimeException(e);
			} else {
				execution.getItem().updateItem(this.stepLineElement.getTransformer().tranform(execution.getItem().getItem()));
			}
		}
		return execution;
	}

	private static RuntimeException buildRuntimeException() {
		return buildRuntimeException(null);
	}

	private static RuntimeException buildRuntimeException(Exception e) {
		return new RuntimeException("Cannot retrieve exception", e);
	}

}
