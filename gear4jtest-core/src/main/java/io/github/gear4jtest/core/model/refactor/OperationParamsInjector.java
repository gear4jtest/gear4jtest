package io.github.gear4jtest.core.model.refactor;

import java.util.ArrayList;
import java.util.List;

import io.github.gear4jtest.core.model.refactor.ProcessingOperationDefinition.ParameterModel;

public class OperationParamsInjector implements Processor {

	@Override
	public void process(Object input, ExecutionContext context, OperationDefinition<?, ?> model, OperationExecution operationExecution) throws Exception {
		var parameters = getModel(model);
		if (parameters == null || parameters.isEmpty()) {
			return;
		}

        for (ParameterModel<?, ?> param : parameters) {
            Parameter parameterValue = param.getParamRetriever().getParameterValue(operationExecution.getOperation());
            if (parameterValue == null) {
                continue;
            }
            Object value = param.getValue(input);
            parameterValue.setValue(value);

//			context.getEventTriggerService().publishEvent(new ParameterInjectionEventBuilder().buildEvent(context.getId(), buildParameterContextualData(value)));
        }

	}

	private static List<ParameterModel<?, ?>> getModel(OperationDefinition<?, ?> model) {
		if (model instanceof ProcessingOperationDefinition<?, ?> processingModel) {
			return processingModel.getParameters();
		}
		return List.of();

	}

//	private ParameterContextualData buildParameterContextualData(Object parameterValue) {
//		return new ParameterContextualData("" /*get parameter name */, parameterValue);
//	}

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

		// default parameter value : to be removed
//		public static <T> Parameter<T> of(T value) {
//			return new Parameter<T>(value);
//		}

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
