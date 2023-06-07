package io.github.gear4jtest.core.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import io.github.gear4jtest.core.context.ItemExecution;
import io.github.gear4jtest.core.context.LineElementExecution;
import io.github.gear4jtest.core.context.StepExecution;
import io.github.gear4jtest.core.factory.ResourceFactory;
import io.github.gear4jtest.core.internal.AssemblyLineBuilder.StepConfiguration;
import io.github.gear4jtest.core.model.BaseOnError;
import io.github.gear4jtest.core.model.OperationModel;
import io.github.gear4jtest.core.model.OperationModel.ParameterModel;
import io.github.gear4jtest.core.processor.BaseProcessor;
import io.github.gear4jtest.core.processor.PostProcessor;
import io.github.gear4jtest.core.processor.PreProcessor;
import io.github.gear4jtest.core.processor.Processor;
import io.github.gear4jtest.core.processor.ProcessorChainTemplate;
import io.github.gear4jtest.core.processor.Transformer;
import io.github.gear4jtest.core.processor.operation.OperationInvoker;

public class StepLineElement extends LineElement {

	private final Class<?> clazz;
	private final ResourceFactory resourceFactory;
	private final Transformer<?, ?> transformer;
	private final boolean ignoreOperationFactoryException = false;
	private final ProcessorChainTemplate processorChain;
	private final StepConfiguration configuration;

	private final Map<Class<? extends Processor>, Object> processorModels;

	private final List<ParameterModel<?, ?>> parameters;

	// build a log context for every stepelement, so that every event will use this
	// context to get contextualized
//	private final BaseContext baseContext;

	public StepLineElement(OperationModel<?, ?> step, StepConfiguration configuration,
			ResourceFactory resourceFactory) {
		super();
		this.clazz = step.getType();
		this.parameters = Collections.unmodifiableList(new ArrayList<>(step.getParameters()));
		this.transformer = step.getTransformer();
		this.processorModels = step.getProcessorModels();

		this.resourceFactory = resourceFactory;
		this.configuration = configuration;

		List<ProcessorInternalModel> elements = buildProcessors(
				getProcessors(step.getPreProcessors(),
						configuration.getStepLineElementDefaultConfiguration().getPreProcessors()),
				getPostProcessors(step.getPostProcessors(),
						configuration.getStepLineElementDefaultConfiguration().getPostProcessors()))
				.stream().map(processor -> buildInternalModel(processor, step)).collect(Collectors.toList());

		this.processorChain = new ProcessorChainTemplate(elements, resourceFactory);
	}

	@Override
	public LineElementExecution createLineElementExecution(ItemExecution itemExecution) {
		return new StepExecution(itemExecution, null);
	}

	@Override
	public Item execute(Item input) {
		return new StepLineElementExecutor(this).execute(input);
	}

	private static List<Class<? extends PreProcessor>> getProcessors(List<Class<? extends PreProcessor>> stepProcessors,
			List<Class<? extends PreProcessor>> defaultProcessors) {
		return stepProcessors != null ? stepProcessors : defaultProcessors;
	}

	private static List<Class<? extends PostProcessor>> getPostProcessors(
			List<Class<? extends PostProcessor>> stepProcessors,
			List<Class<? extends PostProcessor>> defaultProcessors) {
		return stepProcessors != null ? stepProcessors : defaultProcessors;
	}

	private static List<Class<? extends BaseProcessor>> buildProcessors(
			List<Class<? extends PreProcessor>> preProcessors, List<Class<? extends PostProcessor>> postProcessors) {
		List<Class<? extends BaseProcessor>> processors = new ArrayList<>();
		processors.addAll(preProcessors);
		processors.add(OperationInvoker.class);
		processors.addAll(postProcessors);
		return processors;
	}

	private static <T extends BaseProcessor<?, ?>> ProcessorInternalModel buildInternalModel(Class<T> processor,
			OperationModel<?, ?> step) {
		List<BaseOnError> onErrors = Optional.ofNullable(step.getOnErrors()).orElse(Collections.emptyList()).stream()
				.filter(oe -> oe.getProcessor().equals(processor)).collect(Collectors.toList());
		return new ProcessorInternalModel(processor, onErrors);
	}

	public List<ParameterModel<?, ?>> getParameters() {
		return parameters;
	}

	public ProcessorChainTemplate getProcessorChain() {
		return processorChain;
	}

	public Transformer getTransformer() {
		return transformer;
	}

	public boolean isIgnoreOperationFactoryException() {
		return ignoreOperationFactoryException;
	}

	public Class getClazz() {
		return clazz;
	}

	public ResourceFactory getResourceFactory() {
		return resourceFactory;
	}

	public StepConfiguration getConfiguration() {
		return configuration;
	}

	public Map<Class<? extends Processor>, Object> getProcessorModels() {
		return processorModels;
	}

}
