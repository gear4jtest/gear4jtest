package io.github.gear4jtest.core.processor;

import java.util.List;
import java.util.stream.Collectors;

import io.github.gear4jtest.core.internal.Gear4jContext;
import io.github.gear4jtest.core.internal.LineElement;
import io.github.gear4jtest.core.model.OnError;
import io.github.gear4jtest.core.processor.AbstractProcessorChain2.AbstractBaseProcessorChainElement;

public class ProcessorChain<T extends LineElement> {

	private AbstractProcessorChain2<T> chain;
	
	private Object input;
	
	private Gear4jContext context;
	
	private Object result;

	public ProcessorChain(AbstractProcessorChain2<T> chain, Object input, Gear4jContext context) {
		this.chain = chain;
		this.input = input;
		this.context = context;
	}

	public Object processChain() {
		processProcessor(chain.getCurrentProcessor(), input, context);

		return result;
	}

	public void proceed() {
		chain.setCurrentProcessor(chain.getCurrentProcessor().getNextElement());
		processProcessor(chain.getCurrentProcessor(), input, context);
	}
	
	void proceed(Object result) {
		this.result = result;
		proceed();
	}

	private void processProcessor(AbstractBaseProcessorChainElement<T, ?> currentProcessor, Object input,
			Gear4jContext context) {
		if (currentProcessor == null) {
			return;
		}
		try {
			currentProcessor.execute(input, context, chain.getCurrentElement(), this);
		} catch (Exception e) {
			List<OnError> errors = currentProcessor.getOnErrors().stream()
					.filter(error -> isExceptionEligible(e, error.getType()))
					.collect(Collectors.toList());
			if (errors.isEmpty()) {
				throw e;
			}
			for (OnError onError : errors) {
				if (onError.isFatal()) {
					throw e;
				} else if (onError.isProcessorChainFatal()) {
					break;
				} else if (onError.isIgnore()) {

				}
			}
		}
	}
	
	private static boolean isExceptionEligible(Exception e, Class<?> clazz) {
		return clazz == null || clazz.isInstance(e);
	}
	
	public static class BaseProcessorDrivingElement<T extends LineElement> {
		
		protected ProcessorChain<T> chain;

		public BaseProcessorDrivingElement(ProcessorChain<T> chain) {
			this.chain = chain;
		}

	}
	
	public static class ProcessorDrivingElement<T extends LineElement> extends BaseProcessorDrivingElement<T> {

		public ProcessorDrivingElement(ProcessorChain<T> chain) {
			super(chain);
		}

		public void proceed() {
			chain.proceed();
		}
		
	}

	public static class ProcessingProcessorDrivingElement<T extends LineElement> extends BaseProcessorDrivingElement<T> {
		
		public ProcessingProcessorDrivingElement(ProcessorChain<T> chain) {
			super(chain);
		}

		public void proceed(Object result) {
			chain.proceed(result);
		}
		
	}
	
}
