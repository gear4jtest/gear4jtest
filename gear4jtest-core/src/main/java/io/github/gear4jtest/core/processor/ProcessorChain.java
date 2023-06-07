package io.github.gear4jtest.core.processor;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import io.github.gear4jtest.core.context.StepExecution;
import io.github.gear4jtest.core.internal.Item;
import io.github.gear4jtest.core.internal.StepLineElement;
import io.github.gear4jtest.core.model.BaseOnError;
import io.github.gear4jtest.core.model.BaseRule;
import io.github.gear4jtest.core.model.ChainBreakRule;
import io.github.gear4jtest.core.model.FatalRule;
import io.github.gear4jtest.core.model.IgnoreRule;
import io.github.gear4jtest.core.processor.ProcessorChainTemplate.AbstractBaseProcessorChainElement;

public class ProcessorChain {

	private ProcessorChainTemplate chain;

	private Item input;

	private StepExecution context;

	private StepLineElement currentElement;

	private Object result;

	private boolean isInputProcessed;

	public ProcessorChain(ProcessorChainTemplate chain, Item input, StepExecution context, StepLineElement currentElement) {
		this.chain = chain;
		this.input = input;
		this.context = context;
		this.result = input;
		this.currentElement = currentElement;
	}

	public ProcessorChainResult processChain() {
		ProcessorChainResult.Builder processorChainResultBuilder = new ProcessorChainResult.Builder();
		AbstractBaseProcessorChainElement<?, ?> currentProcessor = chain.getCurrentProcessor();
		ProcessorResult processorResult = processProcessor(chain.getCurrentProcessor(), input, context);
		processorChainResultBuilder.processorResult(processorResult);
		while ((currentProcessor = chain.getCurrentProcessor().getNextElement()) != null) {
			processorResult = processProcessor(currentProcessor, input, context);
			processorChainResultBuilder.processorResult(processorResult);
			// trigger event
		}

		processorChainResultBuilder.output(result);
		processorChainResultBuilder.processed(isInputProcessed);

		return processorChainResultBuilder.build();
	}

	public ProcessorResult proceed() {
//		processProcessor(chain.getCurrentProcessor(), input, context);
		return ProcessorResult.succeeded(chain.getCurrentProcessor().getProcessor());
	}

	ProcessorResult proceed(Object result) {
		this.result = result;
		this.isInputProcessed = true;
		return proceed();
	}

	private ProcessorResult processProcessor(AbstractBaseProcessorChainElement<?, ?> currentProcessor, Item input,
			StepExecution context) {
		ProcessorResult result = null;
		try {
			result = currentProcessor.execute(input, context, currentElement, this);
		} catch (Exception e) {
			List<BaseRule> rules = Optional.ofNullable(currentProcessor.getOnErrors()).orElse(Collections.emptyList())
					.stream().map(BaseOnError::getRules).flatMap(List::stream)
					.filter(error -> isExceptionEligible(e, error.getType())).collect(Collectors.toList());
			if (rules.isEmpty()) {
				result = ProcessorResult.failed(currentProcessor.getProcessor(), e);
			} else if (rules.size() > 1) {
				// log to inform client that only the first rule will be taken into account
			} else {
				BaseRule rule = rules.iterator().next();
				if (rule instanceof FatalRule) {
					result = ProcessorResult.failed(currentProcessor.getProcessor(), e);
				} else if (rule instanceof ChainBreakRule) {
					result = ProcessorResult.passedAndBreak(currentProcessor.getProcessor(), e);
				} else if (rule instanceof IgnoreRule) {
					result = ProcessorResult.passedWithWarnings(currentProcessor.getProcessor(), e);
				}
			}
		}
		return result;
	}

	private static boolean isExceptionEligible(Exception e, Class<?> clazz) {
		return clazz == null || clazz.isInstance(e);
	}

	public static class BaseProcessorDriver {

		protected ProcessorChain chain;

		public BaseProcessorDriver(ProcessorChain chain) {
			this.chain = chain;
		}

	}

	public static class ProcessorDriver extends BaseProcessorDriver {

		public ProcessorDriver(ProcessorChain chain) {
			super(chain);
		}

		public ProcessorResult proceed() {
			return chain.proceed();
		}

	}

	public static class ProcessingProcessorDriver extends BaseProcessorDriver {

		public ProcessingProcessorDriver(ProcessorChain chain) {
			super(chain);
		}

		public ProcessorResult proceed(Object result) {
			return chain.proceed(result);
		}

	}

}
