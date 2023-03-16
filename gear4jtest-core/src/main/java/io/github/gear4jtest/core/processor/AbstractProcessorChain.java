package io.github.gear4jtest.core.processor;

import java.util.List;
import java.util.function.Supplier;

import io.github.gear4jtest.core.internal.Gear4jContext;
import io.github.gear4jtest.core.internal.LineElement;
import io.github.gear4jtest.core.internal.ProcessorInternalModel;
import io.github.gear4jtest.core.model.BaseOnError;
import io.github.gear4jtest.core.processor.ProcessorChain.BaseProcessorDrivingElement;
import io.github.gear4jtest.core.processor.ProcessorChain.ProcessingProcessorDrivingElement;
import io.github.gear4jtest.core.processor.ProcessorChain.ProcessorDrivingElement;

public abstract class AbstractProcessorChain<T extends LineElement> {

	private T currentElement;

	private AbstractBaseProcessorChainElement<T, ?> currentProcessor;
	
	abstract Supplier<ProcessingProcessor<T>> getElementProcessor();

	AbstractProcessorChain(List<ProcessorInternalModel<T>> preProcessors, List<ProcessorInternalModel<T>> postProcessors,
			T currentElement) {
		this.currentElement = currentElement;
		buildProcessorsChain(preProcessors, postProcessors);
	}

	private void buildProcessorsChain(List<ProcessorInternalModel<T>> preProcessors, List<ProcessorInternalModel<T>> postProcessors) {
		AbstractBaseProcessorChainElement<T, ?> currentChainedProcessor = null;

		for (ProcessorInternalModel<T> processor : preProcessors) {
			Supplier<? extends Processor<LineElement>> supp;
			AbstractBaseProcessorChainElement<T, ?> newProcessor = new ProcessorChainElement<>(processor.getOnErrors(),
					processor.getProcessor());
			initProcessorChain(currentChainedProcessor, newProcessor);
			currentChainedProcessor = newProcessor;
		}
		AbstractBaseProcessorChainElement<T, ?> newProcessor = new ProcessingProcessorChainElement<>(null, getElementProcessor());
		initProcessorChain(currentChainedProcessor, newProcessor);
		currentChainedProcessor = newProcessor;
		for (ProcessorInternalModel<T> processor : postProcessors) {
			newProcessor = new ProcessorChainElement<>(processor.getOnErrors(), processor.getProcessor());
			initProcessorChain(currentChainedProcessor, newProcessor);
			currentChainedProcessor = newProcessor;
		}
	}

	private void initProcessorChain(AbstractBaseProcessorChainElement<T, ?> currentChainedProcessor,
			AbstractBaseProcessorChainElement<T, ?> newProcessor) {
		if (currentChainedProcessor != null) {
			currentChainedProcessor.setNextElement(newProcessor);
		}
		if (currentProcessor == null) {
			currentProcessor = newProcessor;
		}
	}
	
	AbstractBaseProcessorChainElement<T, ?> getCurrentProcessor() {
		return currentProcessor;
	}
	
	T getCurrentElement() {
		return currentElement;
	}
	
	void setCurrentProcessor(AbstractBaseProcessorChainElement<T, ?> currentProcessor) {
		this.currentProcessor = currentProcessor;
	}

	public static abstract class AbstractBaseProcessorChainElement<T extends LineElement, U extends BaseProcessorDrivingElement<T>> {

		private Supplier<BaseProcessor<T, U>> processor;

		private List<BaseOnError> onErrors;

		private AbstractBaseProcessorChainElement<T, ?> nextElement;

		abstract U getDrivingElement(ProcessorChain<T> chain);
		
		public void execute(Object input, Gear4jContext context, T element, ProcessorChain<T> chain) {
			processor.get().process(input, element, context, getDrivingElement(chain));
		}
		
		public AbstractBaseProcessorChainElement(Supplier<BaseProcessor<T, U>> processor, List<BaseOnError> onErrors) {
			this.processor = processor;
			this.onErrors = onErrors;
		}

		public List<BaseOnError> getOnErrors() {	
			return onErrors;
		}

		public AbstractBaseProcessorChainElement<T, ?> getNextElement() {
			return nextElement;
		}

		void setNextElement(AbstractBaseProcessorChainElement<T, ?> nextElement) {
			this.nextElement = nextElement;
		}

	}

	public static class ProcessorChainElement<T extends LineElement> extends AbstractBaseProcessorChainElement<T, ProcessorDrivingElement<T>> {

		public ProcessorChainElement(List<BaseOnError> onErrors, Supplier<? extends Processor<T>> processor) {
			super((Supplier) processor, onErrors);
		}

		@Override
		ProcessorDrivingElement<T> getDrivingElement(ProcessorChain<T> chain) {
			return new ProcessorDrivingElement<>(chain);
		}

	}

	public static class ProcessingProcessorChainElement<T extends LineElement> extends AbstractBaseProcessorChainElement<T, ProcessingProcessorDrivingElement<T>> {

		public ProcessingProcessorChainElement(List<BaseOnError> onErrors, Supplier<ProcessingProcessor<T>> processor) {
			super((Supplier) processor, onErrors);
		}

		@Override
		ProcessingProcessorDrivingElement<T> getDrivingElement(ProcessorChain<T> chain) {
			return new ProcessingProcessorDrivingElement<T>(chain);
		}

	}

}
