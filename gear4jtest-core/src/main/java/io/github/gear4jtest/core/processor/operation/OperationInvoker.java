package io.github.gear4jtest.core.processor.operation;

import io.github.gear4jtest.core.context.StepProcessingContext;
import io.github.gear4jtest.core.internal.Item;
import io.github.gear4jtest.core.internal.StepLineElement;
import io.github.gear4jtest.core.processor.ProcessingProcessor;
import io.github.gear4jtest.core.processor.ProcessorChain.ProcessingProcessorDrivingElement;
import io.github.gear4jtest.core.processor.ProcessorResult;

public class OperationInvoker implements ProcessingProcessor<StepLineElement, StepProcessingContext> {

	@Override
	public ProcessorResult process(Item input, StepLineElement model, ProcessingProcessorDrivingElement<StepLineElement> chain, StepProcessingContext context) {
		Object result = context.getOperation().execute(input.getItem());
		return chain.proceed(result);
	}
	
}
