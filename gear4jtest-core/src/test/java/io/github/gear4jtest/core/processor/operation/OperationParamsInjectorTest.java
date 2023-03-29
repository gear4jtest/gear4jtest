package io.github.gear4jtest.core.processor.operation;

import static io.github.gear4jtest.core.model.ElementModelBuilders.*;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.github.gear4jtest.core.factory.ResourceFactory;
import io.github.gear4jtest.core.internal.ProcessorInternalModel;
import io.github.gear4jtest.core.internal.StepLineElement;
import io.github.gear4jtest.core.model.OperationModel;
import io.github.gear4jtest.core.processor.ProcessorChain;
import io.github.gear4jtest.core.processor.ProcessorChain.ProcessorDrivingElement;
import io.github.gear4jtest.core.processor.ProcessorChainTemplate;
import io.github.gear4jtest.core.processor.StepProcessingContext;
import io.github.gear4jtest.core.service.steps.Step1;

class OperationParamsInjectorTest {

	private OperationParamsInjector injector = new OperationParamsInjector();

	@Test
	void test() {
		// Given
		OperationModel<String, Integer> operationModel = operation(Step1.class)
				.parameter(newParameter(Step1::getA).value("Value")).build();

		ResourceFactory factory = new ResourceFactory() {
			@Override
			public <T> T getResource(Class<T> clazz) {
				return (T) new Step1();
			}
		};
		
		StepLineElement element = new StepLineElement(operationModel, stepLineElementDefaultConfiguration().build(), factory);
		ProcessorInternalModel<StepLineElement> p = new ProcessorInternalModel<>(OperationParamsInjector.class, emptyList());
		ProcessorChainTemplate<StepLineElement, StepProcessingContext> a = new ProcessorChainTemplate<>(singletonList(p), factory);
		StepProcessingContext ctx = new StepProcessingContext();
		ctx.setOperation(new Step1());
		ProcessorChain chain = new ProcessorChain<>(a, "&", null, element, ctx);

		// When
		injector.process("&", element, ctx, new ProcessorDrivingElement<StepLineElement>(chain), null);

		// Then
		assertThat(((Step1) ctx.getOperation()).getA().getValue()).isEqualTo("Value");
	}

}
