package io.github.gear4jtest.core.internal;

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

	public Item execute(Item input) {
		try {
			Operation<?, ?> operation = stepLineElementOperationInitiator.initiate();

			StepExecution stepProcessorChainExecution = input.getExecution().createStepProcessorChainExecution(operation);
			
			ProcessorChain chain = new ProcessorChain(this.stepLineElement.getProcessorChain(), input, stepProcessorChainExecution,
					this.stepLineElement);
			ProcessorChainResult result = chain.processChain();
			if (!result.isProcessed()) {
				if (this.stepLineElement.getTransformer() == null) {
					throw new IllegalStateException("No transformer specified whereas input has not been processed");
				} else {
					return input.withItem(this.stepLineElement.getTransformer().tranform(input));
				}
			}
			return input.withItem(result.getResult());
		} catch (Exception e) {
			if (this.stepLineElement.getTransformer() == null || !this.stepLineElement.isIgnoreOperationFactoryException()) {
				throw buildRuntimeException(e);
			} else {
				return input.withItem(this.stepLineElement.getTransformer().tranform(input));
			}
		}
	}

	private static RuntimeException buildRuntimeException() {
		return buildRuntimeException(null);
	}

	private static RuntimeException buildRuntimeException(Exception e) {
		return new RuntimeException("Cannot retrieve exception", e);
	}

}
