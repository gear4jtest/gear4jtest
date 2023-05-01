package io.github.gear4jtest.core.processor;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import io.github.gear4jtest.core.context.Contexts;
import io.github.gear4jtest.core.context.LineElementContext;
import io.github.gear4jtest.core.internal.LineElement;
import io.github.gear4jtest.core.model.BaseOnError;
import io.github.gear4jtest.core.model.BaseRule;
import io.github.gear4jtest.core.model.ChainBreakRule;
import io.github.gear4jtest.core.model.FatalRule;
import io.github.gear4jtest.core.model.IgnoreRule;
import io.github.gear4jtest.core.processor.ProcessorChainTemplate.AbstractBaseProcessorChainElement;

public class ProcessorChain<T extends LineElement, V extends LineElementContext> {

	private ProcessorChainTemplate<T, V> chain;
	
	private Object input;
	
	private Contexts<V> context;
	
	private T currentElement;
	
	private Object result;
	
	private boolean isInputProcessed;

	public ProcessorChain(ProcessorChainTemplate<T, V> chain, Object input, Contexts<V> context, T currentElement) {
		this.chain = chain;
		this.input = input;
		this.context = context;
		this.result = input;
		this.currentElement = currentElement;
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

	private void processProcessor(AbstractBaseProcessorChainElement<T, ?, V> currentProcessor, Object input,
			Contexts<V> context) {
		if (currentProcessor == null) {
			return;
		}
		try {
			currentProcessor.execute(input, context, currentElement, this);
		} catch (Exception e) {
			List<BaseRule> rules = Optional.ofNullable(currentProcessor.getOnErrors()).orElse(Collections.emptyList()).stream()
					.map(BaseOnError::getRules)
					.flatMap(List::stream)
					.filter(error -> isExceptionEligible(e, error.getType()))
					.collect(Collectors.toList());
			if (rules.isEmpty()) {
				throw e;
			}
			for (BaseRule rule : rules) {
				if (rule instanceof FatalRule) {
					throw e;
				} else if (rule instanceof ChainBreakRule) {
					break;
				} else if (rule instanceof IgnoreRule) {

				}
			}
		}
	}
	
	private static boolean isExceptionEligible(Exception e, Class<?> clazz) {
		return clazz == null || clazz.isInstance(e);
	}
	
	public static class BaseProcessorDrivingElement<T extends LineElement> {
		
		protected ProcessorChain<T, ?> chain;

		public BaseProcessorDrivingElement(ProcessorChain<T, ?> chain) {
			this.chain = chain;
		}

	}
	
	public static class ProcessorDrivingElement<T extends LineElement> extends BaseProcessorDrivingElement<T> {

		public ProcessorDrivingElement(ProcessorChain<T, ?> chain) {
			super(chain);
		}

		public void proceed() {
			chain.proceed();
		}
		
	}

	public static class ProcessingProcessorDrivingElement<T extends LineElement> extends BaseProcessorDrivingElement<T> {
		
		public ProcessingProcessorDrivingElement(ProcessorChain<T, ?> chain) {
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
