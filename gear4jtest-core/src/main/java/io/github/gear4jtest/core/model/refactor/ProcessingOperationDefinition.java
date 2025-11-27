package io.github.gear4jtest.core.model.refactor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import io.github.gear4jtest.core.model.OperationAdditionalModel;

public class ProcessingOperationDefinition<IN, OUT> extends AbstractOperationDefinition<IN, OUT> {

	/**
	 * Manager de concurrence partagé.
	 *
	 * Si tu veux le scoper à un runtime d'AssemblyLine spécifique,
	 * tu pourras injecter un manager plutôt qu'utiliser ce static.
	 */
	private static final TransformerConcurrencyManager CONCURRENCY_MANAGER =
			new TransformerConcurrencyManager();

	/**
	 * ThreadLocal pour savoir si on a acquis un lock sur CE thread
	 * pour CETTE exécution, et surtout pour ne pas faire d'afterUse()
	 * si beforeUse() a échoué.
	 */
	private static final ThreadLocal<TransformerConcurrencyGuard> CURRENT_GUARD =
			new ThreadLocal<>();

	protected Class<Transformer<IN, OUT>> type;

	protected List<OperationParamsInjector.ParameterModel<?, ?>> parameters;
	
	private OperationConfigurationDefinition operationConfiguration;
	
	ProcessingOperationDefinition() {
		super("", OperationKind.PROCESSING);
	}

	public List<OperationParamsInjector.ParameterModel<?, ?>> getParameters() {
		return parameters;
	}

	public OperationConfigurationDefinition getOperationConfiguration() {
		return operationConfiguration;
	}

	@Override
	public void setUp(IN input, ExecutionContext context, OperationExecutionContext operationExecution) {
		var operation = context.getResourceFactory().getResource(type);
		((DefaultOperationExecutionContext) operationExecution).addCapability(Transformer.class, operation);
		var parameters = OperationParamsInjector.Parameters.newBuilder();
		Optional.ofNullable(this.parameters).stream().flatMap(List::stream).forEach(parameters::withParameter);
		((DefaultOperationExecutionContext) operationExecution).addCapability(OperationParamsInjector.Parameters.class, parameters.build());

		if (!isStateful(operationExecution)) {
			return;
		}

		TransformerConcurrencyGuard guard =
				CONCURRENCY_MANAGER.guardFor(operation, concurrencyStrategy());

		// Si beforeUse() FAIL_FAST et échoue, il va jeter avant qu'on pose le ThreadLocal.
		guard.beforeUse();
		CURRENT_GUARD.set(guard);
	}

	@Override
	public OUT doExecute(IN input, ExecutionContext context, OperationExecutionContext operationExecution) {
		var transformer = OperationContextUtils.<IN, OUT>getTypedTransformer(operationExecution);
		if (transformer.isEmpty()) {
			throw new IllegalStateException("No transformer present found in operation execution context");
		}
		return transformer.get().transform(input, context, operationExecution);
	}

	@Override
	protected void release(OperationExecutionContext context, OUT result, List<Throwable> errors) {
		try {
			if (isStateful(context)) {
				TransformerConcurrencyGuard guard = CURRENT_GUARD.get();
				if (guard != null) {
					guard.afterUse();
				}
			}
		} finally {
			// Nettoyage du ThreadLocal pour éviter les fuites sur les pools de threads
			CURRENT_GUARD.remove();
			// Et on laisse la super-classe faire son éventuel cleanup
			super.release(context, result, errors);
		}
	}

	/**
	 * Stratégie de concurrence utilisée lorsque cette opération est exécutée
	 * de manière concurrente (iteration parallèle, containers parallélisés, etc.).
	 */
	protected TransformerConcurrencyStrategy concurrencyStrategy() {
		return TransformerConcurrencyStrategy.BLOCK_CALLER;
	}

	/**
	 * Indique si cette opération est stateful.
	 * Par défaut, on déduit cela automatiquement depuis le transformer.
	 */
	protected boolean isStateful(OperationExecutionContext operationExecution) {
		var transformer = OperationContextUtils.<IN, OUT>getTypedTransformer(operationExecution);
		return transformer.isPresent() && TransformerIntrospector.isStateful(transformer.get());
	}

	public static class Builder<IN, OUT, OP extends Transformer<IN, OUT>> {

		private final ProcessingOperationDefinition<IN, OUT> managedInstance;

		public Builder() {
			managedInstance = new ProcessingOperationDefinition<>();
		}

		public <A, T extends Transformer<IN, A>> Builder<IN, A, T> type(Class<T> type) {
			managedInstance.type = (Class) type;
			return (Builder<IN, A, T>) this;
		}

		public Builder<IN, OUT, OP> id(String id) {
			managedInstance.id = id;
			return this;
		}

		/**
		 * Paramètre avec valeur fixe.
		 */
		public <A> Builder<IN, OUT, OP> parameter(
				ParamRetriever<OP, A> retriever,
				A value) {

			addParameterInjectorIfNecessary();
			addParameter(new OperationParamsInjector.InterpretationContextParameterModel<>(
					retriever,
					ctx -> value
			));
			return this;
		}

		/**
		 * Paramètre avec Supplier (sans dépendance au contexte).
		 */
		public <A> Builder<IN, OUT, OP> parameter(
				ParamRetriever<OP, A> retriever,
				java.util.function.Supplier<A> supplier) {

			addParameterInjectorIfNecessary();
			addParameter(new OperationParamsInjector.InterpretationContextParameterModel<>(
					retriever,
					ctx -> supplier.get()
			));
			return this;
		}

		/**
		 * Paramètre context-aware : accès à l'input + ExecutionContext + OperationExecutionContext.
		 * C'est ici que tu pourras faire du side compute, etc.
		 */
		public <A> Builder<IN, OUT, OP> parameter(
				ParamRetriever<OP, A> retriever,
				Function<OperationParamsInjector.InterpretationContext<IN>, A> resolver) {

			addParameterInjectorIfNecessary();
			addParameter(new OperationParamsInjector.InterpretationContextParameterModel<>(
					retriever,
					resolver
			));
			return this;
		}

		private void addParameterInjectorIfNecessary() {
			if (managedInstance.processors == null) {
				managedInstance.processors = new ArrayList<>();
			}
			if (managedInstance.processors.stream().noneMatch(p -> p instanceof OperationParamsInjector)) {
				managedInstance.processors.add(new OperationParamsInjector());
			}
		}

		private void addParameter(OperationParamsInjector.ParameterModel<OP, ?> parameterModel) {
			if (managedInstance.parameters == null) {
				managedInstance.parameters = new ArrayList<>();
			}
			managedInstance.parameters.add(parameterModel);
		}
//
//		public <A> Builder<IN, OUT, OP> parameter(ParamRetriever<OP, A> retriever, A value) {
//			addParameterInjectorIfNecessary();
//			addParameter(new OperationParamsInjector.ValueParameterModel<>(retriever, value));
//			return this;
//		}
//
//		public <A> Builder<IN, OUT, OP> parameter(ParamRetriever<OP, A> retriever, Supplier<A> value) {
//			addParameterInjectorIfNecessary();
//			addParameter(new OperationParamsInjector.SupplierParameterModel<>(retriever, value));
//			return this;
//		}
//
//		public <A> Builder<IN, OUT, OP> parameter(ParamRetriever<OP, A> retriever, Function<OperationParamsInjector.InterpretationContextParameterModel.InterpretationContext, A> value) {
//			addParameterInjectorIfNecessary();
//			addParameter(new OperationParamsInjector.InterpretationContextParameterModel<>(retriever, value));
//			return this;
//		}
//
//		private void addParameterInjectorIfNecessary() {
//			if (managedInstance.processors == null) {
//				managedInstance.processors = new ArrayList<>();
//			}
//			if (managedInstance.processors.stream().noneMatch(p -> p instanceof OperationParamsInjector)) {
//				managedInstance.processors.add(new OperationParamsInjector());
//			}
//		}

//		private void addParameter(OperationParamsInjector.ParameterModel<OP, ?> parameterModel) {
//			if (managedInstance.parameters == null) {
//				managedInstance.parameters = new ArrayList<>();
//			}
//			managedInstance.parameters.add(parameterModel);
//		}

		public Builder<IN, OUT, OP> onError(BaseError.SafeError<IN> onError) {
			if (this.managedInstance.onErrors == null) {
				this.managedInstance.onErrors = new ArrayList<>();
			}
			this.managedInstance.onErrors.add(onError);
			return this;
		}

		public UnsafeOperation.Builder<IN, OUT, OP> onError(BaseError.UnSafeError<IN> onError) {
			if (this.managedInstance.onErrors == null) {
				this.managedInstance.onErrors = new ArrayList<>();
			}
			this.managedInstance.onErrors.add(onError);
			return new UnsafeOperation.Builder<>(this);
		}

		public Builder<IN, OUT, OP> fallback(Transformer<IN, OUT> transformer) {
			this.managedInstance.fallbackTransformer = transformer;
			return this;
		}

		public UnsafeOperation.Builder<IN, OUT, OP> conditional(Condition<IN> condition) {
			if (this.managedInstance.conditions == null) {
				this.managedInstance.conditions = new ArrayList<>();
			}
			this.managedInstance.conditions.add(condition);
			return new UnsafeOperation.Builder<>(this);
		}

		public Builder<IN, OUT, OP> additionalModel(OperationAdditionalModel<IN, OUT, OP> model) {
			model.contributeTo(this);
			return this;
		}

		public ProcessingOperationDefinition<IN, OUT> build() {
			return managedInstance;
		}
	}

	@FunctionalInterface
	public interface ParamRetriever<T extends Transformer<?, ?>, U> {

		OperationParamsInjector.Parameter<U> getParameterValue(T operation);

	}

	public static class UnsafeOperation<IN, OUT, OP extends Transformer<IN, OUT>> {

		private ProcessingOperationDefinition.Builder<IN, OUT, OP> operation;

		public static class Builder<IN, OUT, OP extends Transformer<IN, OUT>> {

			private UnsafeOperation<IN, OUT, OP> managedInstance;

			public Builder(ProcessingOperationDefinition.Builder<IN, OUT, OP> operation) {
				this.managedInstance = new UnsafeOperation<>();
				this.managedInstance.operation = operation;
			}

			public Builder<IN, OUT, OP> onError(BaseError.SafeError<IN> onError) {
				this.managedInstance.operation.onError(onError);
				return this;
			}

			public UnsafeOperation.Builder<IN, OUT, OP> onError(BaseError.UnSafeError<IN> onError) {
				this.managedInstance.operation.onError(onError);
				return this;
			}

			public UnsafeOperation.Builder<IN, OUT, OP> conditional(Condition<IN> condition) {
				this.managedInstance.operation.conditional(condition);
				return this;
			}

			public SafeOperation.Builder<IN, OUT, OP> transformer(Transformer<IN, OUT> transformer) {
				this.managedInstance.operation.fallback(transformer);
				return new SafeOperation.Builder<>(this.managedInstance.operation);
			}
		}
	}

	public static class SafeOperation<IN, OUT, OP extends Transformer<IN, OUT>> {

		private ProcessingOperationDefinition.Builder<IN, OUT, OP> operation;

		public static class Builder<IN, OUT, OP extends Transformer<IN, OUT>> {

			private SafeOperation<IN, OUT, OP> managedInstance;

			public Builder(ProcessingOperationDefinition.Builder<IN, OUT, OP> operation) {
				this.managedInstance = new SafeOperation<>();
				this.managedInstance.operation = operation;
			}

			public Builder<IN, OUT, OP> onError(BaseError.SafeError<IN> onError) {
				this.managedInstance.operation.onError(onError);
				return this;
			}

			public Builder<IN, OUT, OP> onError(BaseError.UnSafeError<IN> onError) {
				this.managedInstance.operation.onError(onError);
				return this;
			}

			public Builder<IN, OUT, OP> conditional(Condition<IN> condition) {
				this.managedInstance.operation.conditional(condition);
				return this;
			}

			public ProcessingOperationDefinition.Builder<IN, OUT, OP> transformer(Transformer<IN, OUT> transformer) {
				this.managedInstance.operation.fallback(transformer);
				return this.managedInstance.operation;
			}

			public ProcessingOperationDefinition<IN, OUT> build() {
				return this.managedInstance.operation.build();
			}
		}
	}
}
