//package io.github.gear4jtest.core.processor;
//
//import java.util.List;
//import java.util.stream.Collectors;
//
//import io.github.gear4jtest.core.internal.Gear4jContext;
//import io.github.gear4jtest.core.internal.LineElement;
//import io.github.gear4jtest.core.model.OnError;
//import io.github.gear4jtest.core.model.ProcessorModel;
//
//public abstract class AbstractProcessorChain<T extends LineElement> {
//
//	private T currentElement;
//	private List<ProcessorModel<T>> preProcessors;
//	private List<ProcessorModel<T>> postProcessors;
//
//	abstract ProcessingProcessor<T> getElementProcessor();
//
//	AbstractProcessorChain(List<ProcessorModel<T>> preProcessors, List<ProcessorModel<T>> postProcessors,
//			T currentElement) {
//		this.preProcessors = preProcessors;
//		this.postProcessors = postProcessors;
//		this.currentElement = currentElement;
//	}
//
//	public Object processChain(Object input, Gear4jContext context) {
//		for (ProcessorModel<T> processor : preProcessors) {
//			executeProcessor(processor, input, context);
//		}
//		Object output = getElementProcessor().process(input, currentElement);
//		for (ProcessorModel<T> processor : postProcessors) {
//			executeProcessor(processor, input, context);
//		}
//		return output;
//	}
//
//	private void executeProcessor(ProcessorModel<T> processor, Object input, Gear4jContext context) {
//		try {
//			processor.getProcessor().process(input, currentElement, context);
//		} catch (Exception e) {
//			List<OnError> errors = processor.getOnErrors().stream()
//					.filter(error -> isExceptionEligible(e, error.getClass()))
//					.collect(Collectors.toList());
//			if (errors.isEmpty()) {
//				throw e;
//			}
//			for (OnError onError : errors) {
//				if (onError.isFatal()) {
//					throw e;
//				} else if (onError.isProcessorChainFatal()) {
//					break;
//				} else if (onError.isIgnore()) {
//
//				}
//			}
//		}
//	}
//
//	private static boolean isExceptionEligible(Exception e, Class<?> clazz) {
//		return clazz == null || clazz.isInstance(e);
//	}
//
//}
