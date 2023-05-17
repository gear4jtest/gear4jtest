package io.github.gear4jtest.core.processor.operation;

import static io.github.gear4jtest.core.internal.ServiceRegistry.getEventPublisher;

import java.util.Iterator;
import java.util.List;

import io.github.gear4jtest.core.context.StepProcessingContext;
import io.github.gear4jtest.core.event.builders.ParameterInjectionEventBuilder;
import io.github.gear4jtest.core.internal.Item;
import io.github.gear4jtest.core.internal.StepLineElement;
import io.github.gear4jtest.core.model.OperationModel;
import io.github.gear4jtest.core.processor.PreProcessor;
import io.github.gear4jtest.core.processor.ProcessorChain.ProcessorDrivingElement;
import io.github.gear4jtest.core.processor.ProcessorResult;

public class OperationParamsInjector implements PreProcessor {

	@Override
	public ProcessorResult process(Item input, StepLineElement model, ProcessorDrivingElement<StepLineElement> chain, StepProcessingContext context) {
		List<OperationModel.Parameter<?, ?>> parameters = model.getParameters();
		if (parameters == null || parameters.isEmpty()) {
			return chain.proceed();
		}
//		throw new RuntimeException();

		Iterator<OperationModel.Parameter<?, ?>> it = parameters.iterator();

		while (it.hasNext()) {
			OperationModel.Parameter param = it.next();

			param.getParamRetriever().getParameterValue(context.getOperation()).setValue(param.getValue());

			getEventPublisher(model.getLine().getId()).publishEvent(ParameterInjectionEventBuilder.class, input, model, param.getName(), param.getValue());
		}
		return chain.proceed();
	}

	public static class ParameterValue<T> {

		private T value;

		private ParameterValue() {
		}

		private ParameterValue(T value) {
			this.value = value;
		}

		public static <T> ParameterValue<T> of(T value) {
			return new ParameterValue<T>(value);
		}

		public static <T> ParameterValue<T> of() {
			return new ParameterValue<>();
		}

		public T getValue() {
			return value;
		}

		void setValue(T value) {
			this.value = value;
		}

	}

}
