package io.github.gear4jtest.core.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.github.gear4jtest.core.factory.ResourceFactory;
import io.github.gear4jtest.core.model.BaseOnError;
import io.github.gear4jtest.core.model.ChainModel.StepLineElementDefaultConfiguration;
import io.github.gear4jtest.core.model.Operation;
import io.github.gear4jtest.core.model.OperationModel;
import io.github.gear4jtest.core.model.OperationModel.ChainContextRetriever;
import io.github.gear4jtest.core.model.OperationModel.Parameter;
import io.github.gear4jtest.core.processor.AbstractProcessorChain;
import io.github.gear4jtest.core.processor.Processor;
import io.github.gear4jtest.core.processor.StepProcessorChain;
import io.github.gear4jtest.core.processor.Transformer;

public class StepLineElement extends LineElement {

	private final OperationLazyInitializer operation;
	private final List<Parameter<?, ?>> parameters;
	private final Transformer<?, ?> transformer;
	private final ChainContextRetriever<?> chainContextRetriever;
	private final AbstractProcessorChain<StepLineElement> processorChain;

	public StepLineElement(OperationModel<?, ?> step, StepLineElementDefaultConfiguration defaultConfiguration, ResourceFactory resourceFactory) {
		this.operation = new OperationLazyInitializer(step.getHandler());
		this.parameters = Collections.unmodifiableList(new ArrayList<>(step.getParameters()));
		this.chainContextRetriever = step.getContextRetriever();
		this.transformer = step.getTransformer();
		// build processor models with on error
		this.processorChain = new StepProcessorChain(
				getProcessors(step.getPreProcessors(), defaultConfiguration.getPreProcessors()).stream()
					.map(processor -> buildInternalModel(processor, step, defaultConfiguration, resourceFactory))
					.collect(Collectors.toList()),
				getProcessors(step.getPostProcessors(), defaultConfiguration.getPostProcessors()).stream()
					.map(processor -> buildInternalModel(processor, step, defaultConfiguration, resourceFactory))
					.collect(Collectors.toList()), this);
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

	public AbstractProcessorChain<StepLineElement> getProcessorChain() {
		return processorChain;
	}
	
	public Transformer getTransformer() {
		return transformer;
	}

//	private static List<Class<? extends PreProcessor>> getProcessors(List<Class<? extends PreProcessor>> stepProcessors, List<Class<? extends PreProcessor>> defaultProcessors) {
//		return stepProcessors != null ? stepProcessors : defaultProcessors;
//	}
	
	private static <T> List<T> getProcessors(List<T> stepProcessors, List<T> defaultProcessors) {
		return stepProcessors != null ? stepProcessors : defaultProcessors;
	}
	
	private static <T extends Processor<StepLineElement>> ProcessorInternalModel<StepLineElement> buildInternalModel(Class<T> processor, OperationModel<?, ?> step,
			StepLineElementDefaultConfiguration defaultConfiguration, ResourceFactory resourceFactory) {
		List<BaseOnError> onErrors = Optional.ofNullable(step.getOnErrors()).orElse(Collections.emptyList()).stream()
				.filter(oe -> oe.getProcessor().equals(processor))
				.collect(Collectors.toList());
		Supplier<T> processorSupplier = () -> resourceFactory.getResource(processor);
		return new ProcessorInternalModel<>(processorSupplier, onErrors);
	}

	public static class OperationLazyInitializer {

		private Supplier<Operation> operationSupplier;

		private Operation operation;

		public OperationLazyInitializer(Supplier<?> supplier) {
			this.operationSupplier = (Supplier<Operation>) supplier;
		}

		public Operation getOperation() {
			if (operation == null) {
				operation = operationSupplier.get();
			}
			return operation;
		}

	}

}
