package io.github.gear4jtest.core.internal;

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
	public LineElementExecution execute(StepExecution execution) {
		try {
			Operation<?, ?> operation = stepLineElementOperationInitiator.initiate();

			execution.withOperation(operation);
			ProcessorChain chain = new ProcessorChain(this.stepLineElement.getProcessorChain(), execution.getItemExecution().getItem(),
					execution, operation);
			ProcessorChainResult result = chain.processChain();
			if (!result.isProcessed()) {
				if (this.stepLineElement.getTransformer() == null) {
					throw new IllegalStateException("No transformer specified whereas input has not been processed");
				} else {
					execution.setResult(this.stepLineElement.getTransformer().tranform(execution.getItemExecution().getItem().getItem()));
				}
			} else {
				execution.setResult(result.getOutput());
			}
		} catch (Exception e) {
			if (this.stepLineElement.getTransformer() == null
					|| !this.stepLineElement.isIgnoreOperationFactoryException()) {
				throw buildRuntimeException(e);
			} else {
				execution.setResult(this.stepLineElement.getTransformer().tranform(execution.getItemExecution().getItem().getItem()));
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
