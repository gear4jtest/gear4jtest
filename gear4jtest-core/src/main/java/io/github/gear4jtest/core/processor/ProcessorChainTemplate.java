package io.github.gear4jtest.core.processor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import io.github.gear4jtest.core.context.StepExecution;
import io.github.gear4jtest.core.factory.ResourceFactory;
import io.github.gear4jtest.core.internal.Item;
import io.github.gear4jtest.core.internal.StepLineElement;
import io.github.gear4jtest.core.model.BaseOnError;
import io.github.gear4jtest.core.model.ChainModel.StepLineElementDefaultConfiguration;
import io.github.gear4jtest.core.model.ElementModelBuilders;
import io.github.gear4jtest.core.model.OperationModel;
import io.github.gear4jtest.core.processor.ProcessorChain.BaseProcessorDriver;
import io.github.gear4jtest.core.processor.ProcessorChain.InvokerDriver;
import io.github.gear4jtest.core.processor.ProcessorChain.ProcessorDriver;

public class ProcessorChainTemplate {

//	private AbstractBaseProcessorChainElement<?, ?> currentProcessor;

	private List<AbstractBaseProcessorChainElement<?, ? extends BaseProcessorDriver>> processors;

	private ResourceFactory resourceFactory;

//	public ProcessorChainTemplate(List<ProcessorInternalModel> processors, ResourceFactory resourceFactory) {
//		this.resourceFactory = resourceFactory;
//		buildProcessorsChain(processors);
//	}

	public ProcessorChainTemplate(OperationModel<?, ?> model, StepLineElementDefaultConfiguration globalConfiguration,
			ResourceFactory resourceFactory) {
		this.resourceFactory = resourceFactory;
		StepLineElementDefaultConfiguration localConfiguration = ElementModelBuilders
				.stepLineElementDefaultConfiguration().preProcessors(model.getPreProcessors())
				.postProcessors(model.getPostProcessors())
//				.onError(model.getOnErrors().get(0))
				.build();

		List<Class<? extends PreProcessor>> preProcessors = localConfiguration.getPreProcessors() == null
				? globalConfiguration.getPreProcessors()
				: localConfiguration.getPreProcessors();
		List<Class<? extends PostProcessor>> postProcessors = localConfiguration.getPostProcessors() == null
				? globalConfiguration.getPostProcessors()
				: localConfiguration.getPostProcessors();

		processors = new ArrayList<>();
		processors.addAll(preProcessors.stream().map(this::buildElement).collect(Collectors.toList()));
		processors.add(buildInvoker(globalConfiguration.getInvoker()));
		processors.addAll(postProcessors.stream().map(this::buildElement).collect(Collectors.toList()));
	}

//	private void buildProcessorsChain(List<ProcessorInternalModel> processors) {
//		List<AbstractBaseProcessorChainElement<?, ? extends BaseProcessorDriver>> elements = buildElements(processors);
//
//		Optional<AbstractBaseProcessorChainElement<?, ? extends BaseProcessorDriver>> firstElement = elements.stream()
//				.findFirst();
//		if (firstElement.isPresent()) {
//			currentProcessor = firstElement.get();
//
//			AbstractBaseProcessorChainElement<?, ? extends BaseProcessorDriver> formerChainedProcessor = null;
//			for (AbstractBaseProcessorChainElement<?, ? extends BaseProcessorDriver> element : elements) {
//				if (formerChainedProcessor != null) {
//					formerChainedProcessor.setNextElement(element);
//				}
//				formerChainedProcessor = element;
//			}
//		}
//	}

//	private List<AbstractBaseProcessorChainElement<?, ? extends BaseProcessorDriver>> buildElements(
//			List<ProcessorInternalModel> processors) {
//		return processors.stream().map(this::buildElement).collect(Collectors.toList());
//	}
//
//	private AbstractBaseProcessorChainElement<?, ? extends BaseProcessorDriver> buildElement(
//			ProcessorInternalModel processor) {
//		if (Invoker.class.isAssignableFrom(processor.getProcessor())) {
//			return new ProcessingProcessorChainElement(processor.getOnErrors(),
//					(Class<Invoker>) processor.getProcessor(), resourceFactory);
//		} else if (Processor.class.isAssignableFrom(processor.getProcessor())) {
//			return new ProcessorChainElement(processor.getOnErrors(), (Class<Processor>) processor.getProcessor(),
//					resourceFactory);
//		}
//		return null;
//	}

	public List<AbstractBaseProcessorChainElement<?, ? extends BaseProcessorDriver>> getProcessors() {
		return processors;
	}

	private AbstractBaseProcessorChainElement<?, ProcessorDriver> buildElement(Class<? extends Processor> processor) {
		return new ProcessorChainElement(null, processor, resourceFactory);
	}

	private AbstractBaseProcessorChainElement<?, InvokerDriver> buildInvoker(Class<? extends Invoker> processor) {
		return new ProcessingProcessorChainElement(null, processor, resourceFactory);
	}

//	AbstractBaseProcessorChainElement<?, ?> getCurrentProcessor() {
//		return currentProcessor;
//	}
//
//	void setCurrentProcessor(AbstractBaseProcessorChainElement<?, ?> currentProcessor) {
//		this.currentProcessor = currentProcessor;
//	}

	public static abstract class AbstractBaseProcessorChainElement<T, U extends BaseProcessorDriver> {

		protected Class<? extends BaseProcessor<T, U>> processor;

		private ResourceFactory resourceFactory;

		private List<BaseOnError> onErrors;

		private AbstractBaseProcessorChainElement<?, ? extends BaseProcessorDriver> nextElement;

		abstract U getDrivingElement(ProcessorChain chain);

		public AbstractBaseProcessorChainElement(Class<? extends BaseProcessor<T, U>> processor,
				List<BaseOnError> onErrors, ResourceFactory resourceFactory) {
			this.processor = processor;
			this.onErrors = onErrors;
			this.resourceFactory = resourceFactory;
		}

		public ProcessorResult execute(Item input, StepExecution context, StepLineElement element, ProcessorChain chain) {
			BaseProcessor<T, U> proc = resourceFactory.getResource(processor);
			Object model = Processor.class.isAssignableFrom(processor)
					? element.getProcessorModels().get((Class<? extends Processor>) processor)
					: (Void) null;
			return proc.process(input, (T) model, getDrivingElement(chain), context);
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

		public ProcessorChainElement(List<BaseOnError> onErrors, Class<? extends Processor<T>> processor,
				ResourceFactory resourceFactory) {
			super(processor, onErrors, resourceFactory);
		}

		@Override
		ProcessorDriver getDrivingElement(ProcessorChain chain) {
			return new ProcessorDriver(chain, (Class<? extends Processor<T>>) processor);
		}

	}

	public static class ProcessingProcessorChainElement<T>
			extends AbstractBaseProcessorChainElement<Void, InvokerDriver> {

		public ProcessingProcessorChainElement(List<BaseOnError> onErrors, Class<Invoker> processor,
				ResourceFactory resourceFactory) {
			super(processor, onErrors, resourceFactory);
		}

		@Override
		InvokerDriver getDrivingElement(ProcessorChain chain) {
			return new InvokerDriver(chain, (Class<Invoker>) processor);
		}

	}

}
