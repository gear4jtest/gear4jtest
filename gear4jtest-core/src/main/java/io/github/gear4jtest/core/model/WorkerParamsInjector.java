package io.github.gear4jtest.core.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import io.github.gear4jtest.core.sidecompute.DefaultSideComputeAccessor;
import io.github.gear4jtest.core.sidecompute.SideComputeAccessor;

public class WorkerParamsInjector implements Processor {

	@Override
	public <I> void beforeExecution(I input, StationExecutionContext operationExecution) {
		var processingParameters = StationContextUtils.getProcessingParameters(operationExecution);
		var transformer = StationContextUtils.getRawTransformer(operationExecution);
		if (processingParameters.isEmpty() || transformer.isEmpty()) {
			return;
		}

		// Contexte unique de résolution des paramètres
		InterpretationContext<I> ctx =
				new InterpretationContext<>(input, operationExecution.getGlobalContext(), operationExecution);

		for (ParameterModel<?, ?> rawParam : processingParameters.get().getParameters()) {
			injectParameter(rawParam, transformer.get(), ctx, operationExecution);
		}
	}

	/**
	 * On centralise ici le cast "unsafe" entre le transformer et le modèle de
	 * paramètre. La cohérence est déjà assurée au moment de la définition
	 * (Builder.parameter(...)), donc on peut raisonnablement faire ce cast.
	 */
	@SuppressWarnings("unchecked")
	private <IN, OUT, OP extends Operator<IN, OUT>, T> void injectParameter(ParameterModel<?, ?> rawParam,
                                                                            Operator<?, ?> rawOperator,
                                                                            InterpretationContext<?> ctx,
                                                                            StationExecutionContext operationExecution) {
		// Récupération typée
		ParameterModel<OP, T> param = (ParameterModel<OP, T>) rawParam;
		OP op = (OP) rawOperator;

		WorkerParamsInjector.Parameter<T> parameterValue =
				param.getParamRetriever().getParameterValue(op);

		if (parameterValue == null) {
			return;
		}

        ResolvedParameters cache = operationExecution.getResolvedParameters();
        T value = cache.resolveIfAbsent(rawParam, ctx);
        parameterValue.injectValue(value);

//		T value = param.getValue(ctx);
//		parameterValue.injectValue(value);

		// Event à réactiver si besoin :
		// context.getEventTriggerService()
		//        .publishEvent(new ParameterInjectionEventBuilder()
		//            .withOperationId(operationExecution.getOperationId())
		//            .withParamName(...)
		//            .withValue(...));
	}

	@Override
	public void afterExecution(Object result, StationExecutionContext operationExecution) {
		var processingParameters = StationContextUtils.getProcessingParameters(operationExecution);
		var transformer = StationContextUtils.getRawTransformer(operationExecution);
		if (processingParameters.isEmpty() || transformer.isEmpty()) {
			return;
		}

		for (ParameterModel<?, ?> rawParam : processingParameters.get().getParameters()) {
			cleanupParameter(rawParam, transformer.get());
		}
	}

	@SuppressWarnings("unchecked")
	private <IN, OUT, OP extends Operator<IN, OUT>, T> void cleanupParameter(
			ParameterModel<?, ?> rawParam,
			Operator<?, ?> rawOperator) {

		ParameterModel<OP, T> paramModel = (ParameterModel<OP, T>) rawParam;
		OP op = (OP) rawOperator;

		WorkerParamsInjector.Parameter<T> parameterValue =
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

			public <OP extends Operator<?, ?>, T> Builder withParameter(ParameterModel parameter) {
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

		public static <T> Builder<T> newBuilder() {
			return new Builder<>();
		}

		public static class Builder<T> {

			private LifecyclePolicy lifecyclePolicy = LifecyclePolicy.PERSISTENT;
			private T defaultValue;

			public Builder<T> lifecyclePolicy(LifecyclePolicy lifecyclePolicy) {
				this.lifecyclePolicy = lifecyclePolicy;
				return this;
			}

			public Builder<T> defaultValue(T defaultValue) {
				this.defaultValue = defaultValue;
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

	// ------------------------------------------------------------------------
	// ParameterModel : modèle générique basé sur InterpretationContext
	// ------------------------------------------------------------------------

	public static abstract class ParameterModel<OP extends Operator<?, ?>, T> {

		private final WorkStation.ParamRetriever<OP, T> paramRetriever;

		protected ParameterModel(WorkStation.ParamRetriever<OP, T> paramRetriever) {
			this.paramRetriever = paramRetriever;
		}

		public WorkStation.ParamRetriever<OP, T> getParamRetriever() {
			return paramRetriever;
		}

		/**
		 * Résout la valeur à injecter à partir du contexte d'interprétation.
		 */
		public abstract T getValue(InterpretationContext<?> ctx);
	}

	/**
	 * Implémentation canonique : une fonction (InterpretationContext&lt;IN&gt; -> T).
	 * Tous les paramètres (valeur fixe, supplier, context-aware) sont
	 * traduits vers ce modèle par les Builders.
	 */
	public static class InterpretationContextParameterModel<IN, OP extends Operator<?, ?>, T>
			extends ParameterModel<OP, T> {

		private final Function<InterpretationContext<IN>, T> resolver;

		public InterpretationContextParameterModel(
				WorkStation.ParamRetriever<OP, T> paramRetriever,
				Function<InterpretationContext<IN>, T> resolver) {
			super(paramRetriever);
			this.resolver = resolver;
		}

		@Override
		@SuppressWarnings("unchecked")
		public T getValue(InterpretationContext<?> ctx) {
			return resolver.apply((InterpretationContext<IN>) ctx);
		}
	}

	public static final class InterpretationContext<IN> {

		private final IN item;
		private final ExecutionContext executionContext;
		private final StationExecutionContext stationExecutionContext;
		private final SideComputeAccessor sideComputeAccessor;

		public InterpretationContext(IN item,
									 ExecutionContext executionContext,
									 StationExecutionContext stationExecutionContext) {
			this.item = item;
			this.executionContext = executionContext;
			this.stationExecutionContext = stationExecutionContext;
			this.sideComputeAccessor = stationExecutionContext
                    .getCapability(SideComputeAccessor.class)
                    .orElseGet(() -> new DefaultSideComputeAccessor(executionContext));
		}

		public IN getItem() { return item; }

		public ExecutionContext getExecutionContext() { return executionContext; }

		public StationExecutionContext getOperationExecutionContext() {
			return stationExecutionContext;
		}

		public SideComputeAccessor getSideCompute() { return sideComputeAccessor; }
	}

}
