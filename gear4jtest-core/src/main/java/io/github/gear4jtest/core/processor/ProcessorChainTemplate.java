package io.github.gear4jtest.core.processor;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import io.github.gear4jtest.core.context.StepExecution;
import io.github.gear4jtest.core.factory.ResourceFactory;
import io.github.gear4jtest.core.internal.Item;
import io.github.gear4jtest.core.internal.ProcessorInternalModel;
import io.github.gear4jtest.core.internal.StepLineElement;
import io.github.gear4jtest.core.model.BaseOnError;
import io.github.gear4jtest.core.processor.ProcessorChain.BaseProcessorDriver;
import io.github.gear4jtest.core.processor.ProcessorChain.ProcessingProcessorDriver;
import io.github.gear4jtest.core.processor.ProcessorChain.ProcessorDriver;

public class ProcessorChainTemplate {

	private AbstractBaseProcessorChainElement<?, ?> currentProcessor;
	
	private ResourceFactory resourceFactory;

	public ProcessorChainTemplate(List<ProcessorInternalModel> processors, ResourceFactory resourceFactory) {
		this.resourceFactory = resourceFactory;
		buildProcessorsChain(processors);
	}

	private void buildProcessorsChain(List<ProcessorInternalModel> processors) {
		List<AbstractBaseProcessorChainElement<?, ? extends BaseProcessorDriver>> elements = buildElements(processors);
		
		Optional<AbstractBaseProcessorChainElement<?, ? extends BaseProcessorDriver>> firstElement = elements.stream().findFirst();
		if (firstElement.isPresent()) {
			currentProcessor = firstElement.get();
			
			AbstractBaseProcessorChainElement<?, ? extends BaseProcessorDriver> formerChainedProcessor = null;
			for (AbstractBaseProcessorChainElement<?, ? extends BaseProcessorDriver> element : elements) {
				if (formerChainedProcessor != null) {
					formerChainedProcessor.setNextElement(element);
				}
				formerChainedProcessor = element;
			}
		}
	}
	
	private List<AbstractBaseProcessorChainElement<?, ? extends BaseProcessorDriver>> buildElements(List<ProcessorInternalModel> processors) {
		return processors.stream()
			.map(this::buildElement)
			.collect(Collectors.toList());
	}
	
	private AbstractBaseProcessorChainElement<?, ? extends BaseProcessorDriver> buildElement(ProcessorInternalModel processor) {
		if (ProcessingProcessor.class.isAssignableFrom(processor.getProcessor())) {
			return new ProcessingProcessorChainElement(processor.getOnErrors(), (Class<ProcessingProcessor>) processor.getProcessor(), resourceFactory);
		} else if (Processor.class.isAssignableFrom(processor.getProcessor())) {
			return new ProcessorChainElement(processor.getOnErrors(), (Class<Processor>) processor.getProcessor(), resourceFactory);
		}
		return null;
	}
	
	AbstractBaseProcessorChainElement<?, ?> getCurrentProcessor() {
		return currentProcessor;
	}
	
	void setCurrentProcessor(AbstractBaseProcessorChainElement<?, ?> currentProcessor) {
		this.currentProcessor = currentProcessor;
	}

	public static abstract class AbstractBaseProcessorChainElement<T, U extends BaseProcessorDriver> {

		private Class<? extends BaseProcessor<T, U>> processor;
		
		private ResourceFactory resourceFactory;

		private List<BaseOnError> onErrors;

		private AbstractBaseProcessorChainElement<?, ? extends BaseProcessorDriver> nextElement;

		abstract U getDrivingElement(ProcessorChain chain);
		
		public AbstractBaseProcessorChainElement(Class<? extends BaseProcessor<T, U>> processor, List<BaseOnError> onErrors, ResourceFactory resourceFactory) {
			this.processor = processor;
			this.onErrors = onErrors;
			this.resourceFactory = resourceFactory;
		}
		
		public ProcessorResult execute(Item input, StepExecution context, StepLineElement element, ProcessorChain chain) {
			BaseProcessor<T, U> proc = resourceFactory.getResource(processor);
			return proc.process(input, (T) element.getProcessorModels(processor), getDrivingElement(chain), context);
		}
		
		public Class<? extends BaseProcessor<T, U>> getProcessor() {
			return processor;
		}

		public List<BaseOnError> getOnErrors() {	
			return onErrors;
		}

		public AbstractBaseProcessorChainElement<?, ? extends BaseProcessorDriver> getNextElement() {
			return nextElement;
		}

		void setNextElement(AbstractBaseProcessorChainElement<?, ? extends BaseProcessorDriver> nextElement) {
			this.nextElement = nextElement;
		}

	}

	public static class ProcessorChainElement<T> extends AbstractBaseProcessorChainElement<T, ProcessorDriver> {

		public ProcessorChainElement(List<BaseOnError> onErrors, Class<? extends Processor<T>> processor, ResourceFactory resourceFactory) {
			super(processor, onErrors, resourceFactory);
		}

		@Override
		ProcessorDriver getDrivingElement(ProcessorChain chain) {
			return new ProcessorDriver(chain);
		}

	}

	public static class ProcessingProcessorChainElement<T> extends AbstractBaseProcessorChainElement<Void, ProcessingProcessorDriver> {

		public ProcessingProcessorChainElement(List<BaseOnError> onErrors, Class<ProcessingProcessor> processor, ResourceFactory resourceFactory) {
			super(processor, onErrors, resourceFactory);
		}

		@Override
		ProcessingProcessorDriver getDrivingElement(ProcessorChain chain) {
			return new ProcessingProcessorDriver(chain);
		}

	}

}
