package io.github.gear4jtest.core.processor.operation;

import io.github.gear4jtest.core.context.StepExecution;
import io.github.gear4jtest.core.internal.Item;
import io.github.gear4jtest.core.processor.Invoker;
import io.github.gear4jtest.core.processor.ProcessorChain.InvokerDriver;
import io.github.gear4jtest.core.processor.ProcessorResult;

public class OperationInvoker implements Invoker {

//	@Override
//	public ProcessorResult invoke(Item input, InvokerDriver chainDriver, StepExecution context) {
//		Object result = context.getOperation().execute(input.getItem());
//		return chainDriver.proceed(result);
//	}

	@Override
	public ProcessorResult process(Item input, Void processorModel, InvokerDriver chainDriver, StepExecution context) {
		Object result = context.getOperation().execute(input.getItem());
		return chainDriver.proceed(result);
	}

}
