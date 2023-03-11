package io.github.gear4jtest.core.processor;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import io.github.gear4jtest.core.internal.Gear4jContext;
import io.github.gear4jtest.core.internal.LineElement;
import io.github.gear4jtest.core.model.OnError;
import io.github.gear4jtest.core.model.OnError.Rule;
import io.github.gear4jtest.core.processor.AbstractProcessorChain.AbstractBaseProcessorChainElement;

public class ProcessorChain<T extends LineElement> {

	private AbstractProcessorChain<T> chain;
	
	private Object input;
	
	private Gear4jContext context;
	
	private Object result;
	
	private boolean isInputProcessed;

	public ProcessorChain(AbstractProcessorChain<T> chain, Object input, Gear4jContext context) {
		this.chain = chain;
		this.input = input;
		this.context = context;
		this.result = input;
	}

	public ProcessChainResult processChain() {
		processProcessor(chain.getCurrentProcessor(), input, context);

		return new ProcessChainResult(result, isInputProcessed);
	}

	public void proceed() {
		chain.setCurrentProcessor(chain.getCurrentProcessor().getNextElement());
		processProcessor(chain.getCurrentProcessor(), input, context);
	}
	
	void proceed(Object result) {
		this.result = result;
		this.isInputProcessed = true;
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
			List<Rule> rules = Optional.ofNullable(currentProcessor.getOnErrors()).orElse(Collections.emptyList()).stream()
					.map(OnError::getRules)
					.flatMap(List::stream)
					.filter(error -> isExceptionEligible(e, error.getType()))
					.collect(Collectors.toList());
			if (rules.isEmpty()) {
				throw e;
			}
			for (Rule rule : rules) {
				if (rule.isFatal()) {
					throw e;
				} else if (rule.isProcessorChainFatal()) {
					break;
				} else if (rule.isIgnore()) {

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
	
	public static class ProcessChainResult {
		
		private Object result;
		private boolean processed;

		public ProcessChainResult(Object result, boolean processed) {
			this.result = result;
			this.processed = processed;
		}

		public Object getResult() {
			return result;
		}

		public boolean isProcessed() {
			return processed;
		}
		
	}
	
}
