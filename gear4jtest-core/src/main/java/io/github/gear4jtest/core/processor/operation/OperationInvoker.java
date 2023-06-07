package io.github.gear4jtest.core.processor.operation;

import io.github.gear4jtest.core.context.StepExecution;
import io.github.gear4jtest.core.internal.Item;
import io.github.gear4jtest.core.processor.ProcessingProcessor;
import io.github.gear4jtest.core.processor.ProcessorChain.ProcessingProcessorDriver;
import io.github.gear4jtest.core.processor.ProcessorResult;

public class OperationInvoker implements ProcessingProcessor {

	@Override
	public ProcessorResult process(Item input, Void model, ProcessingProcessorDriver chain, StepExecution context) {
		Object result = context.getOperation().execute(input.getItem());
		return chain.proceed(result);
	}
	
}
