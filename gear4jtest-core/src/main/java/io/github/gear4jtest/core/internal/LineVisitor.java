package io.github.gear4jtest.core.internal;

import io.github.gear4jtest.core.context.Contexts;
import io.github.gear4jtest.core.context.StepProcessingContext;
import io.github.gear4jtest.core.model.Operation;
import io.github.gear4jtest.core.processor.ProcessorChain;
import io.github.gear4jtest.core.processor.ProcessorChain.ProcessChainResult;

public class LineVisitor {
	
	LineVisitor() {
		
	}
	
	private Object visit(BranchesLineElement element, Object input, Contexts context) {
		return input;
	}
	
	private Object visit(BranchLineElement element, Object input, Contexts context) {
		return input;
	}
	
	private Object visit(StepLineElement element, Object input, Contexts<StepProcessingContext> context) {
		StepProcessingContext ctx;
		try {
			Operation<?, ?> operation = element.getOperation().getOperation();
			if (operation == null) {
				throw buildRuntimeException();
			}
			ctx = new StepProcessingContext(operation);
			context.setLineElementContext(ctx);
			ProcessorChain<StepLineElement, StepProcessingContext> chain = new ProcessorChain<>(element.getProcessorChain(), input, context, element);
			ProcessChainResult result = chain.processChain();
			if (!result.isProcessed()) {
				if (element.getTransformer() == null) {
					throw new IllegalStateException("No transformer specified whereas input has not been processed");
				} else {
					return element.getTransformer().tranform(input);
				}
			}
			return result.getResult();
		} catch (Exception e) {
			if (element.getTransformer() == null || !element.isIgnoreOperationFactoryException()) {
				throw buildRuntimeException(e);
			} else {
				return element.getTransformer().tranform(input);
			}
		}
	}
	
	private static RuntimeException buildRuntimeException() {
		return buildRuntimeException(null);
	}
	
	private static RuntimeException buildRuntimeException(Exception e) {
		return new RuntimeException("Cannot retrieve exception", e);
	}
	
	public Object visit(Object element, Object input, Contexts context) {
		if (element instanceof BranchesLineElement) {
			return visit((BranchesLineElement) element, input, context);
		} else if (element instanceof BranchLineElement) {
			return visit((BranchLineElement) element, input, context);
		} else if (element instanceof StepLineElement) {
			return visit((StepLineElement) element, input, context);
		}
		return null;
	}
	
}
