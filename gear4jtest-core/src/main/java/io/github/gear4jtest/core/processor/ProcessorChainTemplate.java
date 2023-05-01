package io.github.gear4jtest.core.processor;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import io.github.gear4jtest.core.context.Gear4jContext;
import io.github.gear4jtest.core.factory.ResourceFactory;
import io.github.gear4jtest.core.internal.LineElement;
import io.github.gear4jtest.core.internal.ProcessorInternalModel;
import io.github.gear4jtest.core.model.BaseOnError;
import io.github.gear4jtest.core.processor.ProcessorChain.BaseProcessorDrivingElement;
import io.github.gear4jtest.core.processor.ProcessorChain.ProcessingProcessorDrivingElement;
import io.github.gear4jtest.core.processor.ProcessorChain.ProcessorDrivingElement;

public class ProcessorChainTemplate<T extends LineElement, V extends BaseProcessingContext<T>> {

	private AbstractBaseProcessorChainElement<T, ?, V> currentProcessor;
	
	private ResourceFactory resourceFactory;

	public ProcessorChainTemplate(List<ProcessorInternalModel<T>> processors, ResourceFactory resourceFactory) {
		this.resourceFactory = resourceFactory;
		buildProcessorsChain(processors);
	}

	private void buildProcessorsChain(List<ProcessorInternalModel<T>> processors) {
		List<AbstractBaseProcessorChainElement<T, ? extends BaseProcessorDrivingElement<T>, V>> elements = buildElements(processors);
		
		Optional<AbstractBaseProcessorChainElement<T, ? extends BaseProcessorDrivingElement<T>, V>> firstElement = elements.stream().findFirst();
		if (firstElement.isPresent()) {
			currentProcessor = firstElement.get();
			
			AbstractBaseProcessorChainElement<T, ? extends BaseProcessorDrivingElement<T>, V> formerChainedProcessor = null;
			for (AbstractBaseProcessorChainElement<T, ? extends BaseProcessorDrivingElement<T>, V> element : elements) {
				if (formerChainedProcessor != null) {
					formerChainedProcessor.setNextElement(element);
				}
				formerChainedProcessor = element;
			}
		}
	}
	
	private List<AbstractBaseProcessorChainElement<T, ? extends BaseProcessorDrivingElement<T>, V>> buildElements(List<ProcessorInternalModel<T>> processors) {
		return processors.stream()
			.map(this::buildElement)
			.collect(Collectors.toList());
	}
	
	private AbstractBaseProcessorChainElement<T, ? extends BaseProcessorDrivingElement<T>, V> buildElement(ProcessorInternalModel<?> processor) {
		if (ProcessingProcessor.class.isAssignableFrom(processor.getProcessor())) {
			return new ProcessingProcessorChainElement<>(processor.getOnErrors(), (Class<ProcessingProcessor<T, V>>) processor.getProcessor(), resourceFactory);
		} else if (Processor.class.isAssignableFrom(processor.getProcessor())) {
			return new ProcessorChainElement(processor.getOnErrors(), (Class<Processor<T, ?>>) processor.getProcessor(), resourceFactory);
		}
		return null;
	}
	
	AbstractBaseProcessorChainElement<T, ?, V> getCurrentProcessor() {
		return currentProcessor;
	}
	
	void setCurrentProcessor(AbstractBaseProcessorChainElement<T, ?, V> currentProcessor) {
		this.currentProcessor = currentProcessor;
	}

	public static abstract class AbstractBaseProcessorChainElement<T extends LineElement, U extends BaseProcessorDrivingElement<T>, V extends BaseProcessingContext<T>> {

		private Class<? extends BaseProcessor<T, U, V>> processor;
		
		private ResourceFactory resourceFactory;

		private List<BaseOnError> onErrors;

		private AbstractBaseProcessorChainElement<T, ? extends BaseProcessorDrivingElement<T>, V> nextElement;

		abstract U getDrivingElement(ProcessorChain<T, V> chain);
		
		public void execute(Object input, Gear4jContext context, T element, ProcessorChain<T, V> chain, V ctx) {
			BaseProcessor<T, U, V> proc = resourceFactory.getResource(processor);
			proc.process(input, element, ctx, getDrivingElement(chain), context);
		}
		
		public AbstractBaseProcessorChainElement(Class<? extends BaseProcessor<T, U, V>> processor, List<BaseOnError> onErrors, ResourceFactory resourceFactory) {
			this.processor = processor;
			this.onErrors = onErrors;
			this.resourceFactory = resourceFactory;
		}

		public List<BaseOnError> getOnErrors() {	
			return onErrors;
		}

		public AbstractBaseProcessorChainElement<T, ? extends BaseProcessorDrivingElement<T>, V> getNextElement() {
			return nextElement;
		}

		void setNextElement(AbstractBaseProcessorChainElement<T, ? extends BaseProcessorDrivingElement<T>, V> nextElement) {
			this.nextElement = nextElement;
		}

	}

	public static class ProcessorChainElement<T extends LineElement, V extends BaseProcessingContext<T>> extends AbstractBaseProcessorChainElement<T, ProcessorDrivingElement<T>, V> {

		public ProcessorChainElement(List<BaseOnError> onErrors, Class<? extends Processor<T, V>> processor, ResourceFactory resourceFactory) {
			super(processor, onErrors, resourceFactory);
		}

		@Override
		ProcessorDrivingElement<T> getDrivingElement(ProcessorChain<T, V> chain) {
			return new ProcessorDrivingElement<>(chain);
		}

	}

	public static class ProcessingProcessorChainElement<T extends LineElement, U, V extends BaseProcessingContext<T>> extends AbstractBaseProcessorChainElement<T, ProcessingProcessorDrivingElement<T>, V> {

		public ProcessingProcessorChainElement(List<BaseOnError> onErrors, Class<ProcessingProcessor<T, V>> processor, ResourceFactory resourceFactory) {
			super(processor, onErrors, resourceFactory);
		}

		@Override
		ProcessingProcessorDrivingElement<T> getDrivingElement(ProcessorChain<T, V> chain) {
			return new ProcessingProcessorDrivingElement<T>(chain);
		}

	}

}
