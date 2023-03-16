package io.github.gear4jtest.core.processor.operation;

import static io.github.gear4jtest.core.model.ElementModelBuilders.newParameter;
import static io.github.gear4jtest.core.model.ElementModelBuilders.operation;
import static io.github.gear4jtest.core.model.ElementModelBuilders.stepLineElementDefaultConfiguration;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.github.gear4jtest.core.factory.ResourceFactory;
import io.github.gear4jtest.core.internal.StepLineElement;
import io.github.gear4jtest.core.model.OperationModel;
import io.github.gear4jtest.core.processor.ProcessorChain;
import io.github.gear4jtest.core.processor.ProcessorChain.ProcessorDrivingElement;
import io.github.gear4jtest.core.processor.StepProcessorChain;
import io.github.gear4jtest.core.service.steps.Step1;

class OperationParamsInjectorTest {

	private OperationParamsInjector injector = new OperationParamsInjector();

	@Test
	void test() {
		// Given
		OperationModel<String, Integer> operationModel = operation(() -> new Step1())
				.parameter(newParameter(Step1::getA).value("Value")).build();

		StepLineElement element = new StepLineElement(operationModel, stepLineElementDefaultConfiguration().build(), new ResourceFactory() {
			@Override
			public <T> T getResource(Class<T> clazz) {
				return null;
			}
		});
		StepProcessorChain chain = new StepProcessorChain(emptyList(), emptyList(), element);

		// When
		injector.process("&", element, null, new ProcessorDrivingElement<StepLineElement>(new ProcessorChain<StepLineElement>(chain, "&", null)));

		// Then
		assertThat(((Step1) element.getOperation().getOperation()).getA().getValue()).isEqualTo("Value");
	}

}
