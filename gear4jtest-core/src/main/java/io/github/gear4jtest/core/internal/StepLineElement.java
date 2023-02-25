package io.github.gear4jtest.core.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import io.github.gear4jtest.core.model.ChainModel.StepLineElementDefaultConfiguration;
import io.github.gear4jtest.core.model.Operation;
import io.github.gear4jtest.core.model.OperationModel;
import io.github.gear4jtest.core.model.OperationModel.ChainContextRetriever;
import io.github.gear4jtest.core.model.OperationModel.Parameter;
import io.github.gear4jtest.core.model.ProcessorModel;
import io.github.gear4jtest.core.processor.AbstractProcessorChain2;
import io.github.gear4jtest.core.processor.StepProcessorChain;

public class StepLineElement extends LineElement {

	private final OperationLazyInitializer operation;
	private final List<Parameter<?, ?>> parameters;
	private final ChainContextRetriever<?> chainContextRetriever;
	private final AbstractProcessorChain2<StepLineElement> processorChain;

	public StepLineElement(OperationModel<?, ?> step, StepLineElementDefaultConfiguration defaultConfiguration) {
		this.operation = new OperationLazyInitializer(step.getHandler());
		this.parameters = Collections.unmodifiableList(new ArrayList<>(step.getParameters()));
		this.chainContextRetriever = step.getContextRetriever();
		this.processorChain = new StepProcessorChain(
				getProcessors(step.getPreProcessors(), defaultConfiguration.getPreProcessors()),
				getProcessors(step.getPostProcessors(), defaultConfiguration.getPostProcessors()), this);
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

	public AbstractProcessorChain2<StepLineElement> getProcessorChain() {
		return processorChain;
	}

	private static List<ProcessorModel<StepLineElement>> getProcessors(List<ProcessorModel<StepLineElement>> stepProcessors,
			List<ProcessorModel<StepLineElement>> defaultProcessors) {
		return stepProcessors != null ? stepProcessors : defaultProcessors;
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
