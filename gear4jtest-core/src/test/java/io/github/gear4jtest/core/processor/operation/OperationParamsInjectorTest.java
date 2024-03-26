//package io.github.gear4jtest.core.processor.operation;
//
//import static io.github.gear4jtest.core.model.ElementModelBuilders.*;
//import static org.assertj.core.api.Assertions.assertThat;
//import static org.mockito.Mockito.verify;
//
//import java.util.HashMap;
//import java.util.Map;
//import java.util.UUID;
//
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.Mock;
//import org.mockito.Mockito;
//import org.mockito.junit.jupiter.MockitoExtension;
//
//import io.github.gear4jtest.core.context.AssemblyLineExecution;
//import io.github.gear4jtest.core.context.ItemExecution;
//import io.github.gear4jtest.core.context.StepExecution;
//import io.github.gear4jtest.core.event.EventTriggerService;
//import io.github.gear4jtest.core.factory.ResourceFactory;
//import io.github.gear4jtest.core.internal.Item;
//import io.github.gear4jtest.core.internal.ServiceRegistry;
//import io.github.gear4jtest.core.internal.StepLineElement;
//import io.github.gear4jtest.core.model.Operation;
//import io.github.gear4jtest.core.model.OperationModel;
//import io.github.gear4jtest.core.processor.ProcessorChain.ProcessorDriver;
//import io.github.gear4jtest.core.processor.operation.OperationParamsInjector.Parameter;
//
//@ExtendWith(MockitoExtension.class)
//class OperationParamsInjectorTest {
//
//	private OperationParamsInjector injector = new OperationParamsInjector();
//
//	@Mock
//	private ResourceFactory resourceFactory;
//	
//	@Mock
//	private ProcessorDriver chain;
//	
//	@Test
//	void shouldInjectParameters() {
//		// Given
//		OperationModel<String, Integer> operationModel = operation(Step1.class)
//				.parameter(newParameter(Step1::getA).value("Value")).build();
//
//		StepLineElement element = new StepLineElement(operationModel, stepLineElementDefaultConfiguration().build(), resourceFactory, null);
//		UUID id = UUID.randomUUID();
//		StepExecution ctx = new StepExecution(new ItemExecution(new AssemblyLineExecution(null, id)), new Step1());
//		ServiceRegistry.EVENT_TRIGGER_SERVICES.put(id, Mockito.mock(EventTriggerService.class));
//		
//		// When
//		injector.process(new Item("&", null), element, chain, ctx);
//
//		// Then
//		assertThat(((Step1) ctx.getOperation()).getA().getValue()).isEqualTo("Value");
//		
//		verify(chain).proceed();
//	}
//
//	@Test
//	void shouldProceedWithoutParameterInjection() {
//		// Given
//		OperationModel<String, Integer> operationModel = operation(Step1.class).build();
//
//		StepLineElement element = new StepLineElement(operationModel, stepLineElementDefaultConfiguration().build(), resourceFactory, null);
//		StepExecution ctx = new StepExecution(null, new Step1());
//		
//		// When
//		injector.process(new Item("&", null), element, chain, ctx);
//
//		// Then
//		assertThat(((Step1) ctx.getOperation()).getA().getValue()).isNull();
//		
//		verify(chain).proceed();
//	}
//	
//	public static class Step1 implements Operation<String, Integer> {
//
//		private Parameter<String> string;
//
//		private final Map<String, Object> chainContext = new HashMap<>();
//
//		public Parameter<String> getA() {
//			return string;
//		}
//		
//		private String b;
//		
//		public String getB() {
//			return b;
//		}
//		
//		@Override
//		public Integer execute(String object) {
//			return 1;
//		}
//
//		public Map<String, Object> getChainContext() {
//			return chainContext;
//		}
//		
//	}
//
//}
