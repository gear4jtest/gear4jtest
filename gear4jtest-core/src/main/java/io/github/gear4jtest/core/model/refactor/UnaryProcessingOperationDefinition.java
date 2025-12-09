package io.github.gear4jtest.core.model.refactor;

import java.util.ArrayList;

public class UnaryProcessingOperationDefinition<INOUT> extends ProcessingOperationDefinition<INOUT, INOUT> {

	private UnaryProcessingOperationDefinition() {
		super();
	}

	public static class Builder<INOUT, OP extends Transformer<INOUT, INOUT>> {

		private final UnaryProcessingOperationDefinition<INOUT> managedInstance;

		public Builder() {
			managedInstance = new UnaryProcessingOperationDefinition<>();
			managedInstance.unary = true;
		}

		public <A, T extends Transformer<INOUT, INOUT>> Builder<INOUT, T> type(Class<T> type) {
			managedInstance.type = (Class) type;
			return (Builder<INOUT, T>) this;
		}

		public Builder<INOUT, OP> id(String id) {
			managedInstance.id = id;
			return this;
		}


		/**
		 * Paramètre avec valeur fixe.
		 */
		public <A> Builder<INOUT, OP> parameter(
				ProcessingOperationDefinition.ParamRetriever<OP, A> retriever,
				A value) {

			addParameterInjectorIfNecessary();
			managedInstance.parameters.add(
					new OperationParamsInjector.InterpretationContextParameterModel<>(
							retriever,
							ctx -> value
					)
			);
			return this;
		}

		/**
		 * Paramètre avec Supplier (sans dépendance au contexte).
		 */
		public <A> Builder<INOUT, OP> parameter(
				ProcessingOperationDefinition.ParamRetriever<OP, A> retriever,
				java.util.function.Supplier<A> supplier) {

			addParameterInjectorIfNecessary();
			managedInstance.parameters.add(
					new OperationParamsInjector.InterpretationContextParameterModel<>(
							retriever,
							ctx -> supplier.get()
					)
			);
			return this;
		}

		/**
		 * Paramètre context-aware.
		 */
		public <A> Builder<INOUT, OP> parameter(
				ProcessingOperationDefinition.ParamRetriever<OP, A> retriever,
				java.util.function.Function<OperationParamsInjector.InterpretationContext<INOUT>, A> resolver) {

			addParameterInjectorIfNecessary();
			managedInstance.parameters.add(
					new OperationParamsInjector.InterpretationContextParameterModel<>(
							retriever,
							resolver
					)
			);
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
//
//		public <A> Builder<INOUT, OP> parameter(ParamRetriever<OP, A> retriever, A value) {
//			addParameterInjectorIfNecessary();
//			managedInstance.parameters.add(new OperationParamsInjector.ValueParameterModel<>(retriever, value));
//			return this;
//		}
//
//		public <A> Builder<INOUT, OP> parameter(ParamRetriever<OP, A> retriever, Supplier<A> value) {
//			addParameterInjectorIfNecessary();
//			managedInstance.parameters.add(new OperationParamsInjector.SupplierParameterModel<>(retriever, value));
//			return this;
//		}
//
//		public <A> Builder<INOUT, OP> parameter(ParamRetriever<OP, A> retriever, Function<OperationParamsInjector.InterpretationContextParameterModel.InterpretationContext, A> value) {
//			addParameterInjectorIfNecessary();
//			managedInstance.parameters.add(new OperationParamsInjector.InterpretationContextParameterModel<>(retriever, value));
//			return this;
//		}
//
//		private void addParameterInjectorIfNecessary() {
//			if (managedInstance.processors.stream().noneMatch(p -> p instanceof OperationParamsInjector)) {
//				managedInstance.processors.add(new OperationParamsInjector());
//			}
//		}

		public Builder<INOUT, OP> onError(BaseError.SafeError<INOUT> onError) {
			this.managedInstance.onErrors.add(onError);
			return this;
		}

		public UnsafeOperation.Builder<INOUT, OP> onError(BaseError.UnSafeError<INOUT> onError) {
			this.managedInstance.onErrors.add(onError);
			return new UnsafeOperation.Builder<>(this);
		}

		public Builder<INOUT, OP> fallback(Transformer<INOUT, INOUT> transformer) {
			this.managedInstance.fallbackTransformer = transformer;
			return this;
		}

		public UnsafeOperation.Builder<INOUT, OP> conditional(Condition<INOUT> condition) {
			this.managedInstance.conditions.add(condition);
			return new UnsafeOperation.Builder<>(this);
		}

		public UnaryProcessingOperationDefinition<INOUT> build() {
			return managedInstance;
		}

	}

	public static class UnsafeOperation<INOUT, OP extends Transformer<INOUT, INOUT>> {

		private UnaryProcessingOperationDefinition.Builder<INOUT, OP> operation;

		public static class Builder<INOUT, OP extends Transformer<INOUT, INOUT>> {

			private UnsafeOperation<INOUT, OP> managedInstance;

			public Builder(UnaryProcessingOperationDefinition.Builder<INOUT, OP> operation) {
				this.managedInstance = new UnsafeOperation<>();
				this.managedInstance.operation = operation;
			}

			public Builder<INOUT, OP> onError(BaseError.SafeError<INOUT> onError) {
				this.managedInstance.operation.onError(onError);
				return this;
			}

			public Builder<INOUT, OP> onError(BaseError.UnSafeError<INOUT> onError) {
				this.managedInstance.operation.onError(onError);
				return this;
			}

			public Builder<INOUT, OP> conditional(Condition<INOUT> condition) {
				this.managedInstance.operation.conditional(condition);
				return this;
			}

			public SafeOperation.Builder<INOUT, OP> transformer(Transformer<INOUT, INOUT> transformer) {
				this.managedInstance.operation.fallback(transformer);
				return new SafeOperation.Builder<>(this.managedInstance.operation);
			}
		}
	}

	public static class SafeOperation<INOUT, OP extends Transformer<INOUT, INOUT>> {

		private UnaryProcessingOperationDefinition.Builder<INOUT, OP> operation;

		public static class Builder<INOUT, OP extends Transformer<INOUT, INOUT>> {

			private SafeOperation<INOUT, OP> managedInstance;

			public Builder(UnaryProcessingOperationDefinition.Builder<INOUT, OP> operation) {
				this.managedInstance = new SafeOperation<>();
				this.managedInstance.operation = operation;
			}

			public Builder<INOUT, OP> onError(BaseError.SafeError<INOUT> onError) {
				this.managedInstance.operation.onError(onError);
				return this;
			}

			public Builder<INOUT, OP> onError(BaseError.UnSafeError<INOUT> onError) {
				this.managedInstance.operation.onError(onError);
				return this;
			}

			public Builder<INOUT, OP> conditional(Condition<INOUT> condition) {
				this.managedInstance.operation.conditional(condition);
				return this;
			}

			public UnaryProcessingOperationDefinition.Builder<INOUT, OP> transformer(Transformer<INOUT, INOUT> transformer) {
				this.managedInstance.operation.fallback(transformer);
				return this.managedInstance.operation;
			}

			public UnaryProcessingOperationDefinition<INOUT> build() {
				return this.managedInstance.operation.build();
			}
		}
	}
}
