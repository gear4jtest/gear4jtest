package io.github.gear4jtest.core.processor.operation;

import io.github.gear4jtest.core.context.Gear4jContext;
import io.github.gear4jtest.core.internal.StepLineElement;
import io.github.gear4jtest.core.processor.ProcessingProcessor;
import io.github.gear4jtest.core.processor.ProcessorChain.ProcessingProcessorDrivingElement;
import io.github.gear4jtest.core.processor.StepProcessingContext;

public class OperationInvoker implements ProcessingProcessor<StepLineElement, StepProcessingContext> {

	@Override
	public void process(Object input, StepLineElement model, StepProcessingContext ctx, ProcessingProcessorDrivingElement<StepLineElement> chain, Gear4jContext context) {
		Object result = ctx.getOperation().execute(input);
		chain.proceed(result);
	}
	
}
