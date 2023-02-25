package io.github.gear4jtest.core.processor;

import java.util.List;

import io.github.gear4jtest.core.internal.StepLineElement;
import io.github.gear4jtest.core.model.ProcessorModel;
import io.github.gear4jtest.core.processor.operation.OperationProcessor;

public class StepProcessorChain extends AbstractProcessorChain2<StepLineElement> {

	public StepProcessorChain(List<ProcessorModel<StepLineElement>> preProcessors,
							  List<ProcessorModel<StepLineElement>> postProcessors, 
			                  StepLineElement currentElement) {
		super(preProcessors, postProcessors, currentElement);
	}

	@Override
	ProcessingProcessor<StepLineElement> getElementProcessor() {
		return new OperationProcessor();
	}

}
