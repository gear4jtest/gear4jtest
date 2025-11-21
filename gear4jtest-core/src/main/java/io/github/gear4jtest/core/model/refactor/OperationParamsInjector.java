package io.github.gear4jtest.core.model.refactor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.github.gear4jtest.core.model.refactor.ProcessingOperationDefinition.ParameterModel;

public class OperationParamsInjector implements Processor {

	@Override
	public void beforeExecution(Object input, OperationExecutionContext operationExecution) {
		var processingParameters = OperationContextUtils.getProcessingParameters(operationExecution);
		var transformer = OperationContextUtils.getRawTransformer(operationExecution);
		if (processingParameters.isEmpty() || transformer.isEmpty()) {
			return;
		}

		for (ParameterModel<?, ?> rawParam : processingParameters.get().getParameters()) {
			injectParameter(rawParam, transformer.get(), input);
		}
	}

	/**
	 * On centralise ici le cast "unsafe" entre le transformer et le modèle de
	 * paramètre. La cohérence est déjà assurée au moment de la définition
	 * (Builder.parameter(...)), donc on peut raisonnablement faire ce cast.
	 */
	@SuppressWarnings("unchecked")
	private <IN, OUT, OP extends Transformer<IN, OUT>, T> void injectParameter(
			ParameterModel<?, ?> rawParam,
			Transformer<?, ?> rawTransformer,
			Object input) {

		// Récupération typée
		ParameterModel<OP, T> param = (ParameterModel<OP, T>) rawParam;
		OP op = (OP) rawTransformer;

		OperationParamsInjector.Parameter<T> parameterValue =
				param.getParamRetriever().getParameterValue(op);

		if (parameterValue == null) {
			return;
		}

		T value = param.getValue(input);
		parameterValue.injectValue(value);

		// Event à réactiver si besoin :
		// context.getEventTriggerService()
		//        .publishEvent(new ParameterInjectionEventBuilder()
		//        .buildEvent(context.getId(), buildParameterContextualData(value)));
	}

	@Override
	public void afterExecution(Object result, OperationExecutionContext operationExecution) {
		var processingParameters = OperationContextUtils.getProcessingParameters(operationExecution);
		var transformer = OperationContextUtils.getRawTransformer(operationExecution);
		if (processingParameters.isEmpty() || transformer.isEmpty()) {
			return;
		}

		var op = transformer.get();

		for (ParameterModel<?, ?> rawParam : processingParameters.get().getParameters()) {
			cleanupParameter(rawParam, op);
		}
	}

	@SuppressWarnings("unchecked")
	private <IN, OUT, OP extends Transformer<IN, OUT>, T> void cleanupParameter(
			ParameterModel<?, ?> rawParam,
			Transformer<?, ?> rawTransformer) {

		ParameterModel<OP, T> paramModel = (ParameterModel<OP, T>) rawParam;
		OP op = (OP) rawTransformer;

		OperationParamsInjector.Parameter<T> parameterValue =
				paramModel.getParamRetriever().getParameterValue(op);

		if (parameterValue != null) {
			parameterValue.afterExecutionCleanup();
		}
	}

	// ------------------------------------------------------------------------
	// Parameters : container pour les modèles de paramètres
	// ------------------------------------------------------------------------

	public static class Parameters {

		private final List<ParameterModel<?, ?>> parameters;

		public Parameters() {
			this.parameters = new ArrayList<>();
		}

		public boolean hasParameters() {
			return !this.parameters.isEmpty();
		}

		public List<ParameterModel<?, ?>> getParameters() {
			return parameters;
		}

		public static Builder newBuilder() {
			return new Builder();
		}

		public static class Builder {

			private final Parameters instance = new Parameters();

			public Builder withParameter(ParameterModel<?, ?> parameter) {
				instance.parameters.add(parameter);
				return this;
			}

			public Builder withParameters(Optional<Parameters> parameters) {
				parameters.ifPresent(p -> instance.parameters.addAll(p.parameters));
				return this;
			}

			public Parameters build() {
				return instance;
			}
		}
	}

	// ------------------------------------------------------------------------
	// Parameter<T> : handle côté opération (read-only pour le transformer)
	// ------------------------------------------------------------------------

	public static class Parameter<T> {

		public enum LifecyclePolicy {
			/** On garde la dernière valeur même après exécution */
			PERSISTENT,
			/** On reset après chaque exécution (ex: gros objets, secrets) */
			PER_EXECUTION
		}

		private final LifecyclePolicy lifecyclePolicy;
		private final T defaultValue;
		private T value;

		private Parameter(Builder<T> builder) {
			this.lifecyclePolicy = builder.lifecyclePolicy;
			this.defaultValue = builder.defaultValue;
			this.value = builder.defaultValue;
		}

		public static <T> Parameter<T> of() {
			return Parameter.<T>builder().build();
		}

		public static <T> Parameter<T> ofDefault(T defaultValue) {
			return Parameter.<T>builder().defaultValue(defaultValue).build();
		}

		public static <T> Builder<T> builder() {
			return new Builder<>();
		}

		public static final class Builder<T> {
			private LifecyclePolicy lifecyclePolicy = LifecyclePolicy.PERSISTENT;
			private T defaultValue = null;

			public Builder<T> defaultValue(T defaultValue) {
				this.defaultValue = defaultValue;
				return this;
			}

			public Builder<T> perExecution() {
				this.lifecyclePolicy = LifecyclePolicy.PER_EXECUTION;
				return this;
			}

			public Parameter<T> build() {
				return new Parameter<>(this);
			}
		}

		public T getValue() {
			return value;
		}

		void injectValue(T newValue) {
			this.value = newValue;
		}

		void afterExecutionCleanup() {
			if (lifecyclePolicy == LifecyclePolicy.PER_EXECUTION) {
				this.value = defaultValue; // souvent null
			}
		}
	}
}
