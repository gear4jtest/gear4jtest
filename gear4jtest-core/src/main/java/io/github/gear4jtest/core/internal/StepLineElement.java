package io.github.gear4jtest.core.internal;

import java.util.Map;

import io.github.gear4jtest.core.context.ItemExecution;
import io.github.gear4jtest.core.context.LineElementExecution;
import io.github.gear4jtest.core.factory.ResourceFactory;
import io.github.gear4jtest.core.internal.AssemblyLineBuilder.StepConfiguration;
import io.github.gear4jtest.core.model.OperationModel;
import io.github.gear4jtest.core.processor.Processor;
import io.github.gear4jtest.core.processor.ProcessorChainTemplate;
import io.github.gear4jtest.core.processor.Transformer;

public class StepLineElement extends LineElement {

	private final Class<?> clazz;
	private final ResourceFactory resourceFactory;
	private final Transformer<?, ?> transformer;
	private final boolean ignoreOperationFactoryException = false;
	private final ProcessorChainTemplate processorChain;
	private final StepConfiguration configuration;

	private final Map<Class<? extends Processor>, Object> processorModels;

	public StepLineElement(OperationModel<?, ?> step, StepConfiguration configuration, ResourceFactory resourceFactory) {
		super();
		this.clazz = step.getType();
		this.transformer = step.getTransformer();
		this.processorModels = step.getProcessorModels();

		this.configuration = configuration;
		this.resourceFactory = resourceFactory;
		this.processorChain = new ProcessorChainTemplate(step, configuration.getStepDefaultConfiguration(), resourceFactory);
	}

//	@Override
//	public LineElementExecution createLineElementExecution(ItemExecution itemExecution) {
//		return new StepExecution(itemExecution, this, null);
//	}

	@Override
	public LineElementExecution execute(ItemExecution itemExecution) {
		return new StepLineElementExecutor(this).execute(itemExecution);
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
