package io.github.gear4jtest.core.processor;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import io.github.gear4jtest.core.context.StepExecution;
import io.github.gear4jtest.core.internal.Item;
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

	private Object result;

	private boolean isInputProcessed;

	public ProcessorChain(ProcessorChainTemplate chain, Item input, StepExecution context) {
		this.chain = chain;
		this.input = input;
		this.context = context;
		this.result = input;
	}

	public ProcessorChainResult processChain() {
		ProcessorChainResult.Builder processorChainResultBuilder = new ProcessorChainResult.Builder();
		
		for (AbstractBaseProcessorChainElement processor : chain.getProcessors()) {
			ProcessorResult processorResult = processProcessor(processor, input, context);
			processorChainResultBuilder.processorResult(processorResult);
		}

		processorChainResultBuilder.output(result);
		processorChainResultBuilder.processed(isInputProcessed);

		return processorChainResultBuilder.build();
	}

//	public ProcessorChainResult processChain() {
//		ProcessorChainResult.Builder processorChainResultBuilder = new ProcessorChainResult.Builder();
//		AbstractBaseProcessorChainElement<?, ?> currentProcessor = chain.getCurrentProcessor();
//		ProcessorResult processorResult = processProcessor(chain.getCurrentProcessor(), input, context);
//		processorChainResultBuilder.processorResult(processorResult);
//		while ((currentProcessor = chain.getCurrentProcessor().getNextElement()) != null) {
//			processorResult = processProcessor(currentProcessor, input, context);
//			processorChainResultBuilder.processorResult(processorResult);
//			// trigger event
//		}
//
//		processorChainResultBuilder.output(result);
//		processorChainResultBuilder.processed(isInputProcessed);
//
//		return processorChainResultBuilder.build();
//	}

	public ProcessorResult proceed(Class<? extends BaseProcessor<?, ?>> processor) {
//		processProcessor(chain.getCurrentProcessor(), input, context);
		return ProcessorResult.succeeded(processor);
	}

	ProcessorResult proceed(Object result, Class<? extends Invoker> processor) {
		this.result = result;
		this.isInputProcessed = true;
		return proceed(processor);
	}

	private ProcessorResult processProcessor(AbstractBaseProcessorChainElement<?, ?> currentProcessor, Item input,
			StepExecution context) {
		ProcessorResult result = null;
		try {
			result = currentProcessor.execute(input, context, this);
		} catch (Exception e) {
			List<BaseRule> rules = Optional.ofNullable(currentProcessor.getOnErrors()).orElse(Collections.emptyList()).stream()
					.map(BaseOnError::getRules).flatMap(List::stream)
					.filter(error -> isExceptionEligible(e, error.getType()))
					.collect(Collectors.toList());
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
		protected Class<? extends BaseProcessor<?, ?>> processor;

		public BaseProcessorDriver(ProcessorChain chain, Class<? extends BaseProcessor<?, ?>> processor) {
			this.chain = chain;
			this.processor = processor;
		}

	}

	public static class ProcessorDriver extends BaseProcessorDriver {

		public ProcessorDriver(ProcessorChain chain, Class<? extends Processor<?>> processor) {
			super(chain, processor);
		}

		public ProcessorResult proceed() {
			return chain.proceed(processor);
		}

	}

	public static class InvokerDriver extends BaseProcessorDriver {

		public InvokerDriver(ProcessorChain chain, Class<? extends Invoker> processor) {
			super(chain, processor);
		}

		public ProcessorResult proceed(Object result) {
			return chain.proceed(result, (Class<? extends Invoker>) processor);
		}

	}

}
