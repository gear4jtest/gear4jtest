package io.github.gear4jtest.core.processor.operation;

import io.github.gear4jtest.core.internal.Gear4jContext;
import io.github.gear4jtest.core.internal.StepLineElement;
import io.github.gear4jtest.core.processor.ProcessingProcessor;
import io.github.gear4jtest.core.processor.ProcessorChain.ProcessingProcessorDrivingElement;

public class OperationProcessor implements ProcessingProcessor<StepLineElement> {

	@Override
	public void process(Object input, StepLineElement model, Gear4jContext context, ProcessingProcessorDrivingElement<StepLineElement> chain) {
		Object result = model.getOperation().getOperation().execute(input);
		chain.proceed(result);
	}
	
}
