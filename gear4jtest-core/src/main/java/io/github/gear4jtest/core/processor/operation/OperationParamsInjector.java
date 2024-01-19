package io.github.gear4jtest.core.processor.operation;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import io.github.gear4jtest.core.context.StepExecution;
import io.github.gear4jtest.core.event.builders.ParameterInjectionEventBuilder;
import io.github.gear4jtest.core.event.builders.ParameterInjectionEventBuilder.ParameterContextualData;
import io.github.gear4jtest.core.internal.Item;
import io.github.gear4jtest.core.model.refactor.ProcessingOperationDataModel;
import io.github.gear4jtest.core.model.refactor.ProcessingOperationDefinition.ParameterModel;
import io.github.gear4jtest.core.processor.ProcessingOperationProcessor;

public class OperationParamsInjector implements ProcessingOperationProcessor {

	@Override
	// see how we can improve the processor mechanism by avoiding this ugly Void parameter (may be see UserModelBasedBaseProcessor)
	public void process(Item input, ProcessingOperationDataModel model, Void a, StepExecution context) {
		Iterator<ParameterModel<?, ?>> it = model.getParameters().iterator();
		while (it.hasNext()) {
			ParameterModel<?, ?> param = it.next();

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
	public boolean isApplicable(ProcessingOperationDataModel model) {
		return model != null/* && model.hasParameters()*/;
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
