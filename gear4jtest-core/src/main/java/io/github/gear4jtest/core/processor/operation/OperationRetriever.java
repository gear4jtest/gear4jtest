package io.github.gear4jtest.core.processor.operation;

import io.github.gear4jtest.core.context.Gear4jContext;
import io.github.gear4jtest.core.internal.StepLineElement;
import io.github.gear4jtest.core.model.Operation;
import io.github.gear4jtest.core.processor.PreProcessor;
import io.github.gear4jtest.core.processor.ProcessorChain.ProcessorDrivingElement;
import io.github.gear4jtest.core.processor.StepProcessingContext;

public class OperationRetriever implements PreProcessor {

	@Override
	public void process(Object input, StepLineElement model, StepProcessingContext ctx, ProcessorDrivingElement<StepLineElement> chain, Gear4jContext context) {
		try {
			Operation<?, ?> operation = model.getOperation().getOperation();
			if (operation == null) {
				throw buildRuntimeException();
			}
			ctx.setOperation(operation);
		} catch (Exception e) {
			throw buildRuntimeException(e);
		}
		chain.proceed();
	}
	
	private static RuntimeException buildRuntimeException() {
		return buildRuntimeException(null);
	}
	
	private static RuntimeException buildRuntimeException(Exception e) {
		return new RuntimeException("Cannot retrieve execption", e);
	}
	
}
