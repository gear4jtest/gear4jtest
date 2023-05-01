package io.github.gear4jtest.core.processor.operation;

import static io.github.gear4jtest.core.model.ElementModelBuilders.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.gear4jtest.core.context.Contexts;
import io.github.gear4jtest.core.context.StepProcessingContext;
import io.github.gear4jtest.core.factory.ResourceFactory;
import io.github.gear4jtest.core.internal.StepLineElement;
import io.github.gear4jtest.core.model.OperationModel;
import io.github.gear4jtest.core.processor.ProcessorChain.ProcessorDrivingElement;
import io.github.gear4jtest.core.service.steps.Step1;

@ExtendWith(MockitoExtension.class)
class OperationParamsInjectorTest {

	private OperationParamsInjector injector = new OperationParamsInjector();

	@Mock
	private ResourceFactory resourceFactory;
	
	@Mock
	private ProcessorDrivingElement<StepLineElement> chain;
	
	@Test
	void shouldInjectParameters() {
		// Given
		OperationModel<String, Integer> operationModel = operation(Step1.class)
				.parameter(newParameter(Step1::getA).value("Value")).build();

		StepLineElement element = new StepLineElement(operationModel, stepLineElementDefaultConfiguration().build(), resourceFactory);
		StepProcessingContext ctx = new StepProcessingContext(new Step1());
		Contexts<StepProcessingContext> ctxs = new Contexts<>();
		ctxs.setLineElementContext(ctx);
		
		// When
		injector.process("&", element, chain, ctxs);

		// Then
		assertThat(((Step1) ctx.getOperation()).getA().getValue()).isEqualTo("Value");
		
		verify(chain).proceed();
	}

	@Test
	void shouldProceedWithoutParameterInjection() {
		// Given
		OperationModel<String, Integer> operationModel = operation(Step1.class).build();

		StepLineElement element = new StepLineElement(operationModel, stepLineElementDefaultConfiguration().build(), resourceFactory);
		StepProcessingContext ctx = new StepProcessingContext(new Step1());
		Contexts<StepProcessingContext> ctxs = new Contexts<>();
		ctxs.setLineElementContext(ctx);
		
		// When
		injector.process("&", element, chain, ctxs);

		// Then
		assertThat(((Step1) ctx.getOperation()).getA().getValue()).isNull();
		
		verify(chain).proceed();
	}

}
