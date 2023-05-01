package io.github.gear4jtest.core.processor.operation;

import io.github.gear4jtest.core.context.Contexts;
import io.github.gear4jtest.core.context.StepProcessingContext;
import io.github.gear4jtest.core.internal.StepLineElement;
import io.github.gear4jtest.core.processor.ProcessingProcessor;
import io.github.gear4jtest.core.processor.ProcessorChain.ProcessingProcessorDrivingElement;

public class OperationInvoker implements ProcessingProcessor<StepLineElement, StepProcessingContext> {

	@Override
	public void process(Object input, StepLineElement model, ProcessingProcessorDrivingElement<StepLineElement> chain, Contexts<StepProcessingContext> context) {
		Object result = context.getLineElementContext().getOperation().execute(input);
		chain.proceed(result);
	}
	
}
