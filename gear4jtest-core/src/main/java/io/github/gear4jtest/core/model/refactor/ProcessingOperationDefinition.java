package io.github.gear4jtest.core.model.refactor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public class ProcessingOperationDefinition<IN, OUT> extends AbstractOperationDefinition<IN, OUT> {

	private Class<Transformer<IN, OUT>> type;

	private List<ParameterModel<?, ?>> parameters;
	
	private OperationConfigurationDefinition operationConfiguration;
	
	private ProcessingOperationDefinition() {
		super("");
		this.parameters = new ArrayList<>();
		this.onErrors = new ArrayList<>();
	}

	public List<ParameterModel<?, ?>> getParameters() {
		return parameters;
	}

	public OperationConfigurationDefinition getOperationConfiguration() {
		return operationConfiguration;
	}

	@Override
	public void initialize(IN input, ExecutionContext context, OperationExecution operationExecution) {
		var operation = context.getResourceFactory().getResource(type);
		operationExecution.setOperation(operation);
	}

	@Override
	public OUT execute(IN input, ExecutionContext context, OperationExecution operationExecution) throws Exception {
		return ((Transformer<IN, OUT>) operationExecution.getOperation()).transform(input, context, operationExecution);
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

		public <A> Builder<IN, OUT, OP> parameter(ParamRetriever<OP, A> retriever, A value) {
			addParameterInjectorIfNecessary();
			managedInstance.parameters.add(new ValueParameterModel<>(retriever, value));
			return this;
		}

		public <A> Builder<IN, OUT, OP> parameter(ParamRetriever<OP, A> retriever, Supplier<A> value) {
			addParameterInjectorIfNecessary();
			managedInstance.parameters.add(new SupplierParameterModel<>(retriever, value));
			return this;
		}

		public <A> Builder<IN, OUT, OP> parameter(ParamRetriever<OP, A> retriever, Function<InterpretationContextParameterModel.InterpretationContext, A> value) {
			addParameterInjectorIfNecessary();
			managedInstance.parameters.add(new InterpretationContextParameterModel<>(retriever, value));
			return this;
		}

		private void addParameterInjectorIfNecessary() {
			if (managedInstance.processors.stream().noneMatch(p -> p instanceof OperationParamsInjector)) {
				managedInstance.processors.add(new OperationParamsInjector());
			}
		}

		public Builder<IN, OUT, OP> onError(BaseError.SafeError<IN> onError) {
			this.managedInstance.onErrors.add(onError);
			return this;
		}

		public UnsafeOperation.Builder<IN, OUT, OP> onError(BaseError.UnSafeError<IN> onError) {
			this.managedInstance.onErrors.add(onError);
			return new UnsafeOperation.Builder<>(this);
		}

		public Builder<IN, OUT, OP> fallback(Transformer<IN, OUT> transformer) {
			this.managedInstance.fallbackTransformer = transformer;
			return this;
		}

		public UnsafeOperation.Builder<IN, OUT, OP> conditional(Condition<IN> condition) {
			this.managedInstance.conditions.add(condition);
			return new UnsafeOperation.Builder<>(this);
		}

		public ProcessingOperationDefinition<IN, OUT> build() {
			return managedInstance;
		}

	}

	@FunctionalInterface
	public interface ParamRetriever<T extends Transformer<?, ?>, U> {

		OperationParamsInjector.Parameter<U> getParameterValue(T operation);

	}

	public static abstract class ParameterModel<OP extends Transformer<?, ?>, T> {

		private ParamRetriever<OP, T> paramRetriever;

		public abstract T getValue(Object item);

		private ParameterModel(ParamRetriever<OP, T> paramRetriever) {
			this.paramRetriever = paramRetriever;
		}

		public ParamRetriever<?, ?> getParamRetriever() {
			return paramRetriever;
		}

	}

	public static class ValueParameterModel<OP extends Transformer<?, ?>, T> extends ParameterModel<OP, T> {

		private T value;

		public ValueParameterModel(ParamRetriever<OP, T> paramRetriever, T value) {
			super(paramRetriever);
			this.value = value;
		}

		@Override
		public T getValue(Object item) {
			return value;
		}

	}

	public static class SupplierParameterModel<OP extends Transformer<?, ?>, T> extends ParameterModel<OP, T> {

		private Supplier<T> value;

		public SupplierParameterModel(ParamRetriever<OP, T> paramRetriever, Supplier<T> value) {
			super(paramRetriever);
			this.value = value;
		}

		@Override
		public T getValue(Object item) {
			return value.get();
		}

	}

	public static class InterpretationContextParameterModel<OP extends Transformer<?, ?>, T>
			extends ParameterModel<OP, T> {

		private Function<InterpretationContext, T> value;

		public InterpretationContextParameterModel(ParamRetriever<OP, T> paramRetriever,
				Function<InterpretationContext, T> value) {
			super(paramRetriever);
			this.value = value;
		}

		private InterpretationContext buildContext(Object item) {
			return new InterpretationContext(item);
		}

		@Override
		public T getValue(Object item) {
			return value.apply(buildContext(item));
		}

		public static class InterpretationContext {

			private Object item;

			public InterpretationContext(Object item) {
				this.item = item;
			}

			public Object getItem() {
				return item;
			}

		}

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
