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
import io.github.gear4jtest.core.model.refactor.OperationConfigurationDefinition;
import io.github.gear4jtest.core.model.refactor.ProcessingOperationDefinition;

public class ProcessorChainTemplate {

//	private AbstractBaseProcessorChainElement<?, ?> currentProcessor;

	private List<AbstractBaseProcessorChainElement<?, ?>> preProcessors;
	private List<AbstractBaseProcessorChainElement<?, ?>> postProcessors;

	private ResourceFactory resourceFactory;

//	public ProcessorChainTemplate(List<ProcessorInternalModel> processors, ResourceFactory resourceFactory) {
//		this.resourceFactory = resourceFactory;
//		buildProcessorsChain(processors);
//	}

	public ProcessorChainTemplate(OperationModel<?, ?> model, OperationConfigurationDefinition globalConfiguration,
			ResourceFactory resourceFactory) {
		this.resourceFactory = resourceFactory;
		StepLineElementDefaultConfiguration localConfiguration = ElementModelBuilders
				.stepLineElementDefaultConfiguration()
				.preProcessors(Optional.ofNullable(model.getPreProcessors()).orElse(Collections.emptyList()))
				.postProcessors(Optional.ofNullable(model.getPostProcessors()).orElse(Collections.emptyList()))
//				.onError(model.getOnErrors().get(0))
				.build();

		List<Class<? extends ProcessingOperationProcessor>> preProcessors = localConfiguration
				.getPreProcessors() == null || localConfiguration.getPreProcessors().isEmpty()
						? globalConfiguration.getPreProcessors()
						: localConfiguration.getPreProcessors();
		List<Class<? extends ProcessingOperationProcessor>> postProcessors = localConfiguration
				.getPostProcessors() == null || localConfiguration.getPostProcessors().isEmpty()
						? globalConfiguration.getPostProcessors()
						: localConfiguration.getPostProcessors();

		this.preProcessors = preProcessors.stream().map(
				p -> buildElement(p, model.getProcessorModels().get(p), getOnErrors(p, model, globalConfiguration)))
				.collect(Collectors.toList());
		this.postProcessors = postProcessors.stream().map(
				p -> buildElement(p, model.getProcessorModels().get(p), getOnErrors(p, model, globalConfiguration)))
				.collect(Collectors.toList());
	}
	
	public ProcessorChainTemplate(ProcessingOperationDefinition<?, ?> model, OperationConfigurationDefinition globalConfiguration,
			ResourceFactory resourceFactory) {
		this.resourceFactory = resourceFactory;
		StepLineElementDefaultConfiguration localConfiguration = ElementModelBuilders
				.stepLineElementDefaultConfiguration()
				.preProcessors(Optional.ofNullable(model.getPreProcessors()).orElse(Collections.emptyList()))
				.postProcessors(Optional.ofNullable(model.getPostProcessors()).orElse(Collections.emptyList()))
//				.onError(model.getOnErrors().get(0))
				.build();

		List<Class<? extends ProcessingOperationProcessor>> preProcessors = localConfiguration
				.getPreProcessors() == null || localConfiguration.getPreProcessors().isEmpty()
						? Optional.ofNullable(globalConfiguration).map(OperationConfigurationDefinition::getPreProcessors).orElse(Collections.emptyList())
						: localConfiguration.getPreProcessors();
		List<Class<? extends ProcessingOperationProcessor>> postProcessors = localConfiguration
				.getPostProcessors() == null || localConfiguration.getPostProcessors().isEmpty()
						? Optional.ofNullable(globalConfiguration).map(OperationConfigurationDefinition::getPostProcessors).orElse(Collections.emptyList())
						: localConfiguration.getPostProcessors();

		this.preProcessors = preProcessors.stream().map(
				p -> buildElement(p, model.getProcessorModels().get(p), getOnErrors(p, model, globalConfiguration)))
				.collect(Collectors.toList());
		this.postProcessors = postProcessors.stream().map(
				p -> buildElement(p, model.getProcessorModels().get(p), getOnErrors(p, model, globalConfiguration)))
				.collect(Collectors.toList());
	}

	private static List<BaseOnError> getOnErrors(Class<? extends BaseProcessor> processor, OperationModel<?, ?> model,
			OperationConfigurationDefinition globalConfiguration) {
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

	private static List<BaseOnError> getOnErrors(Class<? extends BaseProcessor> processor, ProcessingOperationDefinition<?, ?> model,
			OperationConfigurationDefinition globalConfiguration) {
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

	private AbstractBaseProcessorChainElement<?, ?> buildElement(
			Class<? extends ProcessingOperationProcessor> processor, Object model, List<BaseOnError> errors) {
		return new ProcessorChainElement(errors, processor, resourceFactory, model);
	}

//	AbstractBaseProcessorChainElement<?, ?> getCurrentProcessor() {
//		return currentProcessor;
//	}
//
//	void setCurrentProcessor(AbstractBaseProcessorChainElement<?, ?> currentProcessor) {
//		this.currentProcessor = currentProcessor;
//	}

	public List<AbstractBaseProcessorChainElement<?, ?>> getPreProcessors() {
		return preProcessors;
	}

	public List<AbstractBaseProcessorChainElement<?, ?>> getPostProcessors() {
		return postProcessors;
	}

	public static abstract class AbstractBaseProcessorChainElement<T, U> {

		protected Class<? extends BaseProcessor<T, U>> processor;

		private T model;

		private ResourceFactory resourceFactory;

		private List<BaseOnError> onErrors;

		public AbstractBaseProcessorChainElement(Class<? extends BaseProcessor<T, U>> processor,
				List<BaseOnError> onErrors, ResourceFactory resourceFactory, Object model) {
			this.processor = processor;
			this.onErrors = onErrors;
			this.resourceFactory = resourceFactory;
			this.model = (T) model;
		}

		public void execute(Item input, U context, ProcessorChain chain) {
			BaseProcessor<T, U> proc = resourceFactory.getResource(processor);
			proc.process(input, model, context);
		}

		public Class<? extends BaseProcessor<T, U>> getProcessor() {
			return processor;
		}

		public List<BaseOnError> getOnErrors() {
			return onErrors;
		}

	}

	public static class ProcessorChainElement<T> extends AbstractBaseProcessorChainElement<T, StepExecution> {

		public ProcessorChainElement(List<BaseOnError> onErrors,
				Class<? extends ProcessingOperationProcessor<T>> processor, ResourceFactory resourceFactory,
				Object model) {
			super(processor, onErrors, resourceFactory, model);
		}

	}

}
