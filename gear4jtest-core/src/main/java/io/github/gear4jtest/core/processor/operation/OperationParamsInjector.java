package io.github.gear4jtest.core.processor.operation;

import java.util.Iterator;
import java.util.List;

import io.github.gear4jtest.core.context.Contexts;
import io.github.gear4jtest.core.context.StepProcessingContext;
import io.github.gear4jtest.core.internal.StepLineElement;
import io.github.gear4jtest.core.model.OperationModel;
import io.github.gear4jtest.core.processor.PreProcessor;
import io.github.gear4jtest.core.processor.ProcessorChain.ProcessorDrivingElement;

public class OperationParamsInjector implements PreProcessor {

	@Override
	public void process(Object input, StepLineElement model, ProcessorDrivingElement<StepLineElement> chain, Contexts<StepProcessingContext> context) {
		List<OperationModel.Parameter<?, ?>> parameters = model.getParameters();
		if (parameters == null || parameters.isEmpty()) {
			chain.proceed();
			return;
		}

		Iterator<OperationModel.Parameter<?, ?>> it = parameters.iterator();
//		throw new RuntimeException();

		while (it.hasNext()) {
			OperationModel.Parameter param = it.next();

			param.getParamRetriever().getParameterValue(context.getLineElementContext().getOperation())
					.setValue(param.getValue());

			ParameterInjectionEventData data = new ParameterInjectionEventData(param.getName(), param.getValue());
			context.getGlobalContext().getEventTriggerService().triggerEvent("PARAMETER_INJECTION", data, context);
		}
		chain.proceed();
	}

	public static class ParameterInjectionEventData {

		private String name;
		private Object value;

		public ParameterInjectionEventData(String name, Object value) {
			this.name = name;
			this.value = value;
		}

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
