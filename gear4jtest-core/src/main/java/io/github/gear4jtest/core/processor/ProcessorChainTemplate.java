package io.github.gear4jtest.core.processor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import io.github.gear4jtest.core.context.StepExecution;
import io.github.gear4jtest.core.factory.ResourceFactory;
import io.github.gear4jtest.core.internal.Item;
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
				.stepLineElementDefaultConfiguration().preProcessors(Optional.ofNullable(model.getPreProcessors()).orElse(Collections.emptyList()))
				.postProcessors(Optional.ofNullable(model.getPostProcessors()).orElse(Collections.emptyList()))
//				.onError(model.getOnErrors().get(0))
				.build();

		List<Class<? extends PreProcessor>> preProcessors = localConfiguration.getPreProcessors() == null || localConfiguration.getPreProcessors().isEmpty()
				? globalConfiguration.getPreProcessors()
				: localConfiguration.getPreProcessors();
		List<Class<? extends PostProcessor>> postProcessors = localConfiguration.getPostProcessors() == null || localConfiguration.getPostProcessors().isEmpty()
				? globalConfiguration.getPostProcessors()
				: localConfiguration.getPostProcessors();

		processors = new ArrayList<>();
		processors.addAll(preProcessors.stream().map(p -> buildElement(p, model.getProcessorModels().get(p), getOnErrors(p, model, globalConfiguration)))
				.collect(Collectors.toList()));
		processors.add(buildInvoker(globalConfiguration.getInvoker(), getOnErrors(globalConfiguration.getInvoker(), model, globalConfiguration)));
		processors.addAll(postProcessors.stream().map(p -> buildElement(p, model.getProcessorModels().get(p), getOnErrors(p, model, globalConfiguration)))
				.collect(Collectors.toList()));
	}

	private static List<BaseOnError> getOnErrors(Class<? extends BaseProcessor> processor, OperationModel<?, ?> model,
			StepLineElementDefaultConfiguration globalConfiguration) {
		List<BaseOnError> errors = new ArrayList<>();
		List<BaseOnError> globalErrors = globalConfiguration.getOnErrors().stream()
				.filter(boe -> boe.isGlobal() || processor.isAssignableFrom(boe.getProcessor()))
				.collect(Collectors.toList());
		List<BaseOnError> localErrors = model.getOnErrors().stream()
				.filter(boe -> boe.isGlobal() || processor.isAssignableFrom(boe.getProcessor()))
				.collect(Collectors.toList());
		errors.addAll(globalErrors);
		errors.addAll(localErrors);
		return errors;
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

	private AbstractBaseProcessorChainElement<?, ProcessorDriver> buildElement(Class<? extends Processor> processor,
			Object model, List<BaseOnError> errors) {
		return new ProcessorChainElement(errors, processor, resourceFactory, model);
	}

	private AbstractBaseProcessorChainElement<?, InvokerDriver> buildInvoker(Class<? extends Invoker> processor, List<BaseOnError> errors) {
		return new ProcessingProcessorChainElement(errors, processor, resourceFactory);
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

		private T model;

		private ResourceFactory resourceFactory;

		private List<BaseOnError> onErrors;

		private AbstractBaseProcessorChainElement<?, ? extends BaseProcessorDriver> nextElement;

		abstract U getDrivingElement(ProcessorChain chain);

		public AbstractBaseProcessorChainElement(Class<? extends BaseProcessor<T, U>> processor,
				List<BaseOnError> onErrors, ResourceFactory resourceFactory, Object model) {
			this.processor = processor;
			this.onErrors = onErrors;
			this.resourceFactory = resourceFactory;
			this.model = (T) model;
		}

		public ProcessorResult execute(Item input, StepExecution context, ProcessorChain chain) {
			BaseProcessor<T, U> proc = resourceFactory.getResource(processor);
			return proc.process(input, model, getDrivingElement(chain), context);
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
				ResourceFactory resourceFactory, Object model) {
			super(processor, onErrors, resourceFactory, model);
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
			super(processor, onErrors, resourceFactory, (Void) null);
		}

		@Override
		InvokerDriver getDrivingElement(ProcessorChain chain) {
			return new InvokerDriver(chain, (Class<Invoker>) processor);
		}

	}

}
