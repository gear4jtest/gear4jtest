package io.github.gear4jtest.core.internal;

import io.github.gear4jtest.core.context.ItemExecution;
import io.github.gear4jtest.core.context.LineElementExecution;
import io.github.gear4jtest.core.context.StepExecution;
import io.github.gear4jtest.core.model.Operation;
import io.github.gear4jtest.core.processor.ProcessorChain;
import io.github.gear4jtest.core.processor.ProcessorChainResult;

public class StepLineElementExecutor {

	private final StepLineElement stepLineElement;
	private final StepLineElementOperationInitiator stepLineElementOperationInitiator;

	public StepLineElementExecutor(StepLineElement stepLineElement) {
		this.stepLineElement = stepLineElement;
		this.stepLineElementOperationInitiator = new StepLineElementOperationInitiator(stepLineElement);
	}

	// Should itemexecution contains item ?
	public LineElementExecution execute(ItemExecution execution) {
		StepExecution stepProcessorChainExecution = null;
		try {
			Operation<?, ?> operation = stepLineElementOperationInitiator.initiate();

			stepProcessorChainExecution = execution.createStepProcessorChainExecution(stepLineElement, operation);

			ProcessorChain chain = new ProcessorChain(this.stepLineElement.getProcessorChain(), execution.getItem(),
					stepProcessorChainExecution);
			ProcessorChainResult result = chain.processChain();
			if (!result.isProcessed()) {
				if (this.stepLineElement.getTransformer() == null) {
					throw new IllegalStateException("No transformer specified whereas input has not been processed");
				} else {
					execution.updateItem(this.stepLineElement.getTransformer().tranform(execution.getItem().getItem()));
				}
			} else {
				execution.updateItem(result.getOutput());
			}
		} catch (Exception e) {
			if (this.stepLineElement.getTransformer() == null
					|| !this.stepLineElement.isIgnoreOperationFactoryException()) {
				throw buildRuntimeException(e);
			} else {
				execution.updateItem(this.stepLineElement.getTransformer().tranform(execution.getItem().getItem()));
			}
		}
		return stepProcessorChainExecution;
	}

	private static RuntimeException buildRuntimeException() {
		return buildRuntimeException(null);
	}

	private static RuntimeException buildRuntimeException(Exception e) {
		return new RuntimeException("Cannot retrieve exception", e);
	}

}
