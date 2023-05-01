package io.github.gear4jtest.core.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.github.gear4jtest.core.context.StepProcessingContext;
import io.github.gear4jtest.core.factory.ResourceFactory;
import io.github.gear4jtest.core.model.BaseOnError;
import io.github.gear4jtest.core.model.ChainModel.StepLineElementDefaultConfiguration;
import io.github.gear4jtest.core.model.Operation;
import io.github.gear4jtest.core.model.OperationModel;
import io.github.gear4jtest.core.model.OperationModel.ChainContextRetriever;
import io.github.gear4jtest.core.model.OperationModel.Parameter;
import io.github.gear4jtest.core.processor.BaseProcessor;
import io.github.gear4jtest.core.processor.PostProcessor;
import io.github.gear4jtest.core.processor.PreProcessor;
import io.github.gear4jtest.core.processor.ProcessorChainTemplate;
import io.github.gear4jtest.core.processor.Transformer;
import io.github.gear4jtest.core.processor.operation.OperationInvoker;

public class StepLineElement extends LineElement {

	private final OperationLazyInitializer operation;
	private final List<Parameter<?, ?>> parameters;
	private final Transformer<?, ?> transformer;
	private final ChainContextRetriever<?> chainContextRetriever;
	private final boolean ignoreOperationFactoryException = false;
	private final ProcessorChainTemplate<StepLineElement, StepProcessingContext> processorChain;
	
	// build a log context for every stepelement, so that every event will use this context to get contextualized
//	private final BaseContext baseContext;
	private final UUID elementUuid;

	public StepLineElement(OperationModel<?, ?> step, StepLineElementDefaultConfiguration defaultConfiguration,
			ResourceFactory resourceFactory) {
		this.elementUuid = UUID.randomUUID();
		this.operation = new OperationLazyInitializer(step.getType(), resourceFactory);
		this.parameters = Collections.unmodifiableList(new ArrayList<>(step.getParameters()));
		this.chainContextRetriever = step.getContextRetriever();
		this.transformer = step.getTransformer();

		List<ProcessorInternalModel<StepLineElement>> elements = buildProcessors(getProcessors(step.getPreProcessors(), defaultConfiguration.getPreProcessors()),
				getProcessors(step.getPostProcessors(), defaultConfiguration.getPostProcessors())).stream()
			.map(processor -> buildInternalModel(processor, step, defaultConfiguration))
			.collect(Collectors.toList());

		this.processorChain = new ProcessorChainTemplate<>(elements, resourceFactory);
		// build processor models with on error
//		this.processorChain = new StepProcessorChain(
//				getProcessors(step.getPreProcessors(), defaultConfiguration.getPreProcessors()).stream()
//						.map(processor -> buildInternalModel(processor, step, defaultConfiguration, resourceFactory))
//						.collect(Collectors.toList()),
//				getProcessors(step.getPostProcessors(), defaultConfiguration.getPostProcessors()).stream()
//						.map(processor -> buildInternalModel(processor, step, defaultConfiguration, resourceFactory))
//						.collect(Collectors.toList()),
//				this, step.getOnErrors());
	}

	public OperationLazyInitializer getOperation() {
		return operation;
	}

	public List<Parameter<?, ?>> getParameters() {
		return parameters;
	}

	public ChainContextRetriever<?> getChainContextRetriever() {
		return chainContextRetriever;
	}

	public ProcessorChainTemplate<StepLineElement, StepProcessingContext> getProcessorChain() {
		return processorChain;
	}

	public Transformer getTransformer() {
		return transformer;
	}
	
	public boolean isIgnoreOperationFactoryException() {
		return ignoreOperationFactoryException;
	}

//	private static List<Class<? extends PreProcessor>> getProcessors(List<Class<? extends PreProcessor>> stepProcessors, List<Class<? extends PreProcessor>> defaultProcessors) {
//		return stepProcessors != null ? stepProcessors : defaultProcessors;
//	}

	private static <T> List<T> getProcessors(List<T> stepProcessors, List<T> defaultProcessors) {
		return stepProcessors != null ? stepProcessors : defaultProcessors;
	}

	private static List<Class<? extends BaseProcessor<StepLineElement, ?, ?>>> buildProcessors(
			List<Class<? extends PreProcessor>> preProcessors, List<Class<? extends PostProcessor>> postProcessors) {
		return Stream.of(preProcessors.stream(), Stream.of(OperationInvoker.class), postProcessors.stream())
				.flatMap(Function.identity()).collect(Collectors.toList());
	}

	private static <T extends BaseProcessor<StepLineElement, ?, ?>> ProcessorInternalModel<StepLineElement> buildInternalModel(
			Class<T> processor, OperationModel<?, ?> step, StepLineElementDefaultConfiguration defaultConfiguration) {
		List<BaseOnError> onErrors = Optional.ofNullable(step.getOnErrors()).orElse(Collections.emptyList()).stream()
				.filter(oe -> oe.getProcessor().equals(processor)).collect(Collectors.toList());
		return new ProcessorInternalModel<>(processor, onErrors);
	}

	public static class OperationLazyInitializer {

		private Class<Operation> type;

		private ResourceFactory resourceFactory;

		private Operation operation;

		public OperationLazyInitializer(Class<?> type, ResourceFactory resourceFactory) {
			this.type = (Class<Operation>) type;
			this.resourceFactory = resourceFactory;
		}

		public Operation getOperation() {
			if (operation == null) {
				operation = resourceFactory.getResource(type);
			}
			return operation;
		}

	}

}
