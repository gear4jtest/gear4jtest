package io.github.gear4jtest.core.internal;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.github.gear4jtest.core.context.StepExecution;
import io.github.gear4jtest.core.event.Event;
import io.github.gear4jtest.core.event.SimpleEventTriggerService;
import io.github.gear4jtest.core.event.builders.ParameterInjectionEventBuilder;
import io.github.gear4jtest.core.event.builders.ParameterInjectionEventBuilder.ParameterContextualData;
import io.github.gear4jtest.core.internal.AssemblyLineBuilder.StepConfiguration;
import io.github.gear4jtest.core.model.Operation;
import io.github.gear4jtest.core.processor.operation.OperationParamsInjector.Parameter;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;

public class OperationParametersInitializer {

	private final StepConfiguration configuration;
	private final Operation<?, ?> operation;
	private final StepExecution execution;
	private final LineElement element;

	public OperationParametersInitializer(StepLineElement stepLineElement, Operation<?, ?> operation, StepExecution execution) {
		this.configuration = stepLineElement.getConfiguration();
		this.operation = operation;
		this.execution = execution;
		this.element = stepLineElement;
	}

	public Operation<?, ?> initialize() throws IllegalArgumentException, IllegalAccessException {
		List<Field> fields = Stream.of(operation.getClass().getDeclaredFields())
				.filter(field -> Parameter.class.isAssignableFrom(field.getType()))
				.collect(Collectors.toList());

		for (Field field : fields) {
			if (field.get(operation) == null) {
				Parameter parameter = Parameter.of();
//				if (configuration.getEventConfiguration().isEventOnParameterChanged()) {
//					Enhancer enhancer = new Enhancer();
//					enhancer.setSuperclass(Parameter.class);
//					enhancer.setCallback(new ParameterChangedFireEventInterceptor(field.getName(), execution.getEventTriggerService(), element));
//					Parameter proxiedParameter = (Parameter) enhancer.create();
//					field.setAccessible(true);
//					field.set(proxiedParameter, operation);
//					field.setAccessible(false);
//				} else {
//					field.setAccessible(true);
//					field.set(parameter, operation);
//					field.setAccessible(false);
//				}
			}
		}
		return operation;
	}

	public static abstract class AbstractEventPublisherMethodInterceptor implements MethodInterceptor {

		private final String methodName;
		private final SimpleEventTriggerService eventTriggerService;

		abstract Event buildEvent(Object obj, Method method, Object[] args, MethodProxy proxy);
		
		public AbstractEventPublisherMethodInterceptor(String methodName, SimpleEventTriggerService eventTriggerService) {
			this.methodName = methodName;
			this.eventTriggerService = eventTriggerService;
		}

		@Override
		public Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy) throws Throwable {
			Object invocation = proxy.invokeSuper(obj, args);
			if (method.getName().equals(methodName)) {
				eventTriggerService.publishEvent(buildEvent(obj, method, args, proxy));
			}
			return invocation;
		}

	}

	public static class ParameterChangedFireEventInterceptor extends AbstractEventPublisherMethodInterceptor {

		private static final String METHOD_NAME = "setValue";
		
		private final String parameterName;
		private final LineElement element;
		
		public ParameterChangedFireEventInterceptor(String parameterName, SimpleEventTriggerService eventTriggerService, LineElement element) {
			super(METHOD_NAME, eventTriggerService);
			this.parameterName = parameterName;
			this.element = element;
		}

		@Override
		Event buildEvent(Object obj, Method method, Object[] args, MethodProxy proxy) {
			return new ParameterInjectionEventBuilder().buildEvent(element.getId(), buildParameterContextualData(args[0]));
		}
		
		private ParameterContextualData buildParameterContextualData(Object parameterValue) {
			return new ParameterContextualData(this.parameterName, parameterValue);
		}

	}

}
