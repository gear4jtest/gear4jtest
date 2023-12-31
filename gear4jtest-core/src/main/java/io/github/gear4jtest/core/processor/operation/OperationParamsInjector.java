package io.github.gear4jtest.core.processor.operation;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import io.github.gear4jtest.core.context.StepExecution;
import io.github.gear4jtest.core.event.builders.ParameterInjectionEventBuilder;
import io.github.gear4jtest.core.event.builders.ParameterInjectionEventBuilder.ParameterContextualData;
import io.github.gear4jtest.core.internal.Item;
import io.github.gear4jtest.core.model.OperationModel;
import io.github.gear4jtest.core.model.OperationModel.ParameterModel;
import io.github.gear4jtest.core.processor.ProcessingOperationProcessor;
import io.github.gear4jtest.core.processor.operation.OperationParamsInjector.Parameters;

public class OperationParamsInjector implements ProcessingOperationProcessor<Parameters> {

	@Override
	public void process(Item input, Parameters model, StepExecution context) {
		Iterator<ParameterModel<?, ?>> it = model.getParameters().iterator();
		while (it.hasNext()) {
			OperationModel.ParameterModel param = it.next();

			Parameter parameterValue = param.getParamRetriever().getParameterValue(context.getOperation());
			if (parameterValue == null) {

			}
			Object value = param.getValue(input.getItem());
			parameterValue.setValue(value);
			
			context.getEventTriggerService().publishEvent(new ParameterInjectionEventBuilder().buildEvent(context.getId(), buildParameterContextualData(value)));
		}
	}
	
	private ParameterContextualData buildParameterContextualData(Object parameterValue) {
		return new ParameterContextualData("" /*get parameter name */, parameterValue);
	}

	// instance method => runtime processor chain eligibility. Make eligibility goes before processing, at processor chain building
	@Override
	public boolean isApplicable(Parameters model) {
		return model != null && model.hasParameters();
	}

	public static class Parameters {

		private List<ParameterModel<?, ?>> parameters;

		public Parameters() {
			this.parameters = new ArrayList<>();
		}

		public boolean hasParameters() {
			return this.parameters != null && !this.parameters.isEmpty();
		}

		public List<ParameterModel<?, ?>> getParameters() {
			return parameters;
		}

		public static Builder newBuilder() {
			return new Builder();
		}

		public static class Builder {

			private Parameters instance = new Parameters();

			public Builder withParameter(ParameterModel<?, ?> parameter) {
				instance.parameters.add(parameter);
				return this;
			}

			public Parameters build() {
				return instance;
			}

		}

	}

	public static class Parameter<T> {

		private T value;

		private Parameter() {
		}

		private Parameter(T value) {
			this.value = value;
		}

		public static <T> Parameter<T> of(T value) {
			return new Parameter<T>(value);
		}

		public static <T> Parameter<T> of() {
			return new Parameter<>();
		}

		public T getValue() {
			return value;
		}

		void setValue(T value) {
			this.value = value;
		}

	}

}
