//package io.github.gear4jtest.core.internal;
//
//import io.github.gear4jtest.core.context.BranchExecution;
//import io.github.gear4jtest.core.context.BranchesExecution;
//import io.github.gear4jtest.core.context.StepProcessorChainExecution;
//import io.github.gear4jtest.core.model.Operation;
//import io.github.gear4jtest.core.processor.ProcessorChain;
//import io.github.gear4jtest.core.processor.ProcessorChainResult;
//
//public class LineVisitor {
//	
//	LineVisitor() {
//		
//	}
//	
//	private Item visit(BranchesLineElement element, Item input) {
//		BranchesExecution branchesExecution = input.getExecution().createBranchesExecution();
//		return input;
//	}
//	
//	private Item visit(BranchLineElement element, Item input) {
//		BranchExecution branchExecution = input.getExecution().createBranchExecution();
//		return input;
//	}
//	
//	private Item visit(StepLineElement element, Item input) {
//		try {
//			Operation<?, ?> operation = element.getOperation().getOperation();
//			if (operation == null) {
//				throw buildRuntimeException();
//			}
//			StepProcessorChainExecution stepProcessorChainExecution = input.getExecution().createStepProcessorChainExecution(operation);
//			ProcessorChain chain = new ProcessorChain(element.getProcessorChain(), input, stepProcessorChainExecution, element);
//			ProcessorChainResult result = chain.processChain();
//			if (!result.isProcessed()) {
//				if (element.getTransformer() == null) {
//					throw new IllegalStateException("No transformer specified whereas input has not been processed");
//				} else {
//					return input.withItem(element.getTransformer().tranform(input));
//				}
//			}
//			return input.withItem(result.getResult());
//		} catch (Exception e) {
//			if (element.getTransformer() == null || !element.isIgnoreOperationFactoryException()) {
//				throw buildRuntimeException(e);
//			} else {
//				return input.withItem(element.getTransformer().tranform(input));
//			}
//		}
//	}
//	
//	private static RuntimeException buildRuntimeException() {
//		return buildRuntimeException(null);
//	}
//	
//	private static RuntimeException buildRuntimeException(Exception e) {
//		return new RuntimeException("Cannot retrieve exception", e);
//	}
//	
//	public Item visit(Object element, Item input) {
//		if (element instanceof BranchesLineElement) {
//			return visit((BranchesLineElement) element, input);
//		} else if (element instanceof BranchLineElement) {
//			return visit((BranchLineElement) element, input);
//		} else if (element instanceof StepLineElement) {
//			return visit((StepLineElement) element, input);
//		}
//		return null;
//	}
//	
//}
