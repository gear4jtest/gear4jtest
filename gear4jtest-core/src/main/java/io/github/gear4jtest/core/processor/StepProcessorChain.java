package io.github.gear4jtest.core.processor;

import java.util.List;
import java.util.function.Supplier;

import io.github.gear4jtest.core.internal.ProcessorInternalModel;
import io.github.gear4jtest.core.internal.StepLineElement;
import io.github.gear4jtest.core.model.ProcessorModel;
import io.github.gear4jtest.core.processor.operation.OperationProcessor;

public class StepProcessorChain extends AbstractProcessorChain<StepLineElement> {

	public StepProcessorChain(List<ProcessorInternalModel<StepLineElement>> preProcessors,
							  List<ProcessorInternalModel<StepLineElement>> postProcessors, 
			                  StepLineElement currentElement) {
		super(preProcessors, postProcessors, currentElement);
	}

	@Override
	Supplier<ProcessingProcessor<StepLineElement>> getElementProcessor() {
		return OperationProcessor::new;
	}

}
