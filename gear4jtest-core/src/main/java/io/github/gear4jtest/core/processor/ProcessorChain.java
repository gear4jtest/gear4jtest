package io.github.gear4jtest.core.processor;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import io.github.gear4jtest.core.internal.Gear4jContext;
import io.github.gear4jtest.core.internal.LineElement;
import io.github.gear4jtest.core.model.BaseOnError;
import io.github.gear4jtest.core.model.BaseRule;
import io.github.gear4jtest.core.model.ChainBreakRule;
import io.github.gear4jtest.core.model.FatalRule;
import io.github.gear4jtest.core.model.IgnoreRule;
import io.github.gear4jtest.core.processor.ProcessorChainTemplate.AbstractBaseProcessorChainElement;

public class ProcessorChain<T extends LineElement, V extends BaseProcessingContext<T>> {

	private ProcessorChainTemplate<T, V> chain;
	
	private Object input;
	
	private Gear4jContext context;
	
	private T currentElement;
	
	private V ctx;
	
	private Object result;
	
	private boolean isInputProcessed;

	public ProcessorChain(ProcessorChainTemplate<T, V> chain, Object input, Gear4jContext context, T currentElement, V ctx) {
		this.chain = chain;
		this.input = input;
		this.context = context;
		this.result = input;
		this.currentElement = currentElement;
		this.ctx = ctx;
	}

	public ProcessChainResult processChain() {
		processProcessor(chain.getCurrentProcessor(), input, context, ctx);

		return new ProcessChainResult(result, isInputProcessed);
	}

	public void proceed() {
		chain.setCurrentProcessor(chain.getCurrentProcessor().getNextElement());
		processProcessor(chain.getCurrentProcessor(), input, context, ctx);
	}
	
	void proceed(Object result) {
		this.result = result;
		this.isInputProcessed = true;
		proceed();
	}

	private void processProcessor(AbstractBaseProcessorChainElement<T, ?, V> currentProcessor, Object input,
			Gear4jContext context, V ctx) {
		if (currentProcessor == null) {
			return;
		}
		try {
			currentProcessor.execute(input, context, currentElement, this, ctx);
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
