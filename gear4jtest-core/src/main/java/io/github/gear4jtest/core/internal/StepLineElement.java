package io.github.gear4jtest.core.internal;

import io.github.gear4jtest.core.context.LineElementExecution;
import io.github.gear4jtest.core.context.StepExecution;
import io.github.gear4jtest.core.factory.ResourceFactory;
import io.github.gear4jtest.core.internal.AssemblyLineBuilder.StepConfiguration;
import io.github.gear4jtest.core.model.refactor.ProcessingOperationDataModel;
import io.github.gear4jtest.core.model.refactor.ProcessingOperationDefinition;
import io.github.gear4jtest.core.processor.ProcessorChainTemplate;
import io.github.gear4jtest.core.processor.Transformer;

public class StepLineElement extends LineElement<StepExecution> {

	private final Class<?> clazz;
	private final ResourceFactory resourceFactory;
	private final Transformer<?, ?> transformer;
	private final boolean ignoreOperationFactoryException = false;
	private final ProcessorChainTemplate processorChain;
	private final StepConfiguration configuration;

//	private final Map<Class<? extends ProcessingOperationProcessor>, Object> processorModels;
	private final ProcessingOperationDataModel processingOperationDataModel;

//	public StepLineElement(OperationModel<?, ?> step, StepConfiguration configuration, ResourceFactory resourceFactory) {
//		super();
//		this.clazz = step.getType();
//		this.transformer = step.getTransformer();
//		this.processingOperationDataModel = null;
//
//		this.configuration = configuration;
//		this.resourceFactory = resourceFactory;
//		this.processorChain = new ProcessorChainTemplate(step, configuration.getStepDefaultConfiguration(), resourceFactory);
//	}
	
	public StepLineElement(ProcessingOperationDefinition<?, ?> step, StepConfiguration configuration, ResourceFactory resourceFactory) {
		super();
		this.clazz = step.getType();
		this.transformer = step.getTransformer();
		this.processingOperationDataModel = new ProcessingOperationDataModel(step.getParameters());

		this.configuration = configuration;
		this.resourceFactory = resourceFactory;
		this.processorChain = new ProcessorChainTemplate(step, configuration.getStepDefaultConfiguration(), resourceFactory);
	}

//	@Override
//	public LineElementExecution createLineElementExecution(ItemExecution itemExecution) {
//		return new StepExecution(itemExecution, this, null);
//	}

	@Override
	public LineElementExecution execute(StepExecution itemExecution) {
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

	public ProcessingOperationDataModel getProcessingOperationDataModel() {
		return processingOperationDataModel;
	}

//	public Map<Class<? extends ProcessingOperationProcessor>, Object> getProcessorModels() {
//		return processorModels;
//	}

}
