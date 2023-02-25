package io.github.gear4jtest.core.processor;

import java.util.List;

import io.github.gear4jtest.core.internal.Gear4jContext;
import io.github.gear4jtest.core.internal.LineElement;
import io.github.gear4jtest.core.model.OnError;
import io.github.gear4jtest.core.model.ProcessorModel;
import io.github.gear4jtest.core.processor.ProcessorChain.BaseProcessorDrivingElement;
import io.github.gear4jtest.core.processor.ProcessorChain.ProcessingProcessorDrivingElement;
import io.github.gear4jtest.core.processor.ProcessorChain.ProcessorDrivingElement;

// Decomposer cette chaine en 2 : un template de chaine pour la construciton de la chaine a proprement parler, et à runtime la chaine avec l'input + le context
public abstract class AbstractProcessorChain2<T extends LineElement> {

	private T currentElement;

	private AbstractBaseProcessorChainElement<T, ?> currentProcessor;
	
	abstract ProcessingProcessor<T> getElementProcessor();

	AbstractProcessorChain2(List<ProcessorModel<T>> preProcessors, List<ProcessorModel<T>> postProcessors,
			T currentElement) {
		this.currentElement = currentElement;
		buildProcessorsChain(preProcessors, postProcessors);
	}

	private void buildProcessorsChain(List<ProcessorModel<T>> preProcessors, List<ProcessorModel<T>> postProcessors) {
		AbstractBaseProcessorChainElement<T, ?> currentChainedProcessor = null;

		for (ProcessorModel<T> processor : preProcessors) {
			AbstractBaseProcessorChainElement<T, ?> newProcessor = new ProcessorChainElement<>(processor.getOnErrors(),
					processor.getProcessor());
			initProcessorChain(currentChainedProcessor, newProcessor);
			currentChainedProcessor = newProcessor;
		}
		AbstractBaseProcessorChainElement<T, ?> newProcessor = new ProcessingProcessorChainElement<>(null, getElementProcessor());
		initProcessorChain(currentChainedProcessor, newProcessor);
		currentChainedProcessor = newProcessor;
		for (ProcessorModel<T> processor : postProcessors) {
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

		private BaseProcessor<T, U> processor;

		private List<OnError> onErrors;

		private AbstractBaseProcessorChainElement<T, ?> nextElement;

		abstract U getDrivingElement(ProcessorChain<T> chain);
		
		public void execute(Object input, Gear4jContext context, T element, ProcessorChain<T> chain) {
			processor.process(input, element, context, getDrivingElement(chain));
		}
		
		public AbstractBaseProcessorChainElement(BaseProcessor<T, U> processor, List<OnError> onErrors) {
			this.processor = processor;
			this.onErrors = onErrors;
		}

		public List<OnError> getOnErrors() {
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

		public ProcessorChainElement(List<OnError> onErrors, Processor<T> processor) {
			super(processor, onErrors);
		}

		@Override
		ProcessorDrivingElement<T> getDrivingElement(ProcessorChain<T> chain) {
			return new ProcessorDrivingElement<>(chain);
		}

	}

	public static class ProcessingProcessorChainElement<T extends LineElement> extends AbstractBaseProcessorChainElement<T, ProcessingProcessorDrivingElement<T>> {

		public ProcessingProcessorChainElement(List<OnError> onErrors, ProcessingProcessor<T> processor) {
			super(processor, onErrors);
		}

		@Override
		ProcessingProcessorDrivingElement<T> getDrivingElement(ProcessorChain<T> chain) {
			return new ProcessingProcessorDrivingElement<T>(chain);
		}

	}

}
