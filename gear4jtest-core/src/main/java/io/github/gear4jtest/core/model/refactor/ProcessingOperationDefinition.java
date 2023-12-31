package io.github.gear4jtest.core.model.refactor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import io.github.gear4jtest.core.model.BaseOnError;
import io.github.gear4jtest.core.model.Operation;
import io.github.gear4jtest.core.model.UnsafeOnError;
import io.github.gear4jtest.core.model.refactor.ProcessingOperationDefinition.InterpretationContextParameterModel.InterpretationContext;
import io.github.gear4jtest.core.processor.ProcessingOperationProcessor;
import io.github.gear4jtest.core.processor.Transformer;
import io.github.gear4jtest.core.processor.operation.OperationParamsInjector.Parameter;

public class ProcessingOperationDefinition<IN, OUT> extends OperationDefinition<IN, OUT> {

	private Class<Operation<IN, OUT>> type;

	private List<ParameterModel<?, ?>> parameters;

	private List<Class<? extends ProcessingOperationProcessor>> preProcessors;

	private List<Class<? extends ProcessingOperationProcessor>> postProcessors;

	private List<BaseOnError> onErrors;

	private Transformer<IN, OUT> transformer;

	private Map<Class<? extends ProcessingOperationProcessor>, Object> processorModels;
	
	private OperationConfigurationDefinition operationConfiguration;
	
	private ProcessingOperationDefinition() {
		this.parameters = new ArrayList<>();
		this.onErrors = new ArrayList<>();
		this.processorModels = new HashMap<>();
	}

	public Class<Operation<IN, OUT>> getType() {
		return type;
	}

	public List<ParameterModel<?, ?>> getParameters() {
		return parameters;
	}

	public List<Class<? extends ProcessingOperationProcessor>> getPreProcessors() {
		return preProcessors;
	}

	public List<Class<? extends ProcessingOperationProcessor>> getPostProcessors() {
		return postProcessors;
	}

	public List<BaseOnError> getOnErrors() {
		return onErrors;
	}

	public Transformer<IN, OUT> getTransformer() {
		return transformer;
	}

	public Map<Class<? extends ProcessingOperationProcessor>, Object> getProcessorModels() {
		return processorModels;
	}

	public OperationConfigurationDefinition getOperationConfiguration() {
		return operationConfiguration;
	}

	public static class Builder<IN, OUT, OP extends io.github.gear4jtest.core.model.Operation<IN, OUT>> {

		private final ProcessingOperationDefinition<IN, OUT> managedInstance;

		public Builder() {
			managedInstance = new ProcessingOperationDefinition<>();
		}

		public <A, T extends io.github.gear4jtest.core.model.Operation<IN, A>> Builder<IN, A, T> type(Class<T> type) {
			managedInstance.type = (Class) type;
			return (Builder<IN, A, T>) this;
		}

//				public Builder<IN, OUT> parameter(String name, String evaluator, String value) {
//					managedInstance.parameters.add(new Parameter(name, value, evaluator));
//					return this;
//				}

//				public <A> Builder<IN, OUT> parameter(ParamRetriever<A> name, String evaluator, String value) {
//					managedInstance.parameters.add(new Parameter("", value, evaluator));
//					return this;
//				}
		//
//				public <A> Builder<IN, OUT> parameter(ParamRetriever<A> name, String value) {
//					managedInstance.parameters.add(new Parameter("", value, evaluator));
//					return this;
//				}

//				public <A> Builder<IN, OUT, OP> parameter(ParameterModel<OP, A> parameter) {
//					managedInstance.parameters.add(parameter);
//					return this;
//				}

//		public <A> Builder<IN, OUT, OP> processorModel(Class<? extends ProcessingOperationProcessor<A>> processor, A model) {
//			managedInstance.processorModels.put(processor, model);
//			return this;
//		}

		public <A> Builder<IN, OUT, OP> parameter(ParamRetriever<OP, A> retriever, A value) {
			managedInstance.parameters.add(new ValueParameterModel<>(retriever, value));
			return this;
		}

		public <A> Builder<IN, OUT, OP> parameter(ParamRetriever<OP, A> retriever, Supplier<A> value) {
			managedInstance.parameters.add(new SupplierParameterModel<>(retriever, value));
			return this;
		}

		public <A> Builder<IN, OUT, OP> parameter(ParamRetriever<OP, A> retriever, Function<InterpretationContext, A> value) {
			managedInstance.parameters.add(new InterpretationContextParameterModel<>(retriever, value));
			return this;
		}

//				public <A, B> Parameter<B> newParameter(ParamRetriever<A, B> name) {
//					Parameter<B> param = new Parameter<>(name);
//					return param;
//				}

//				public Builder<IN, OUT> parameter(String name, Object value) {
//					managedInstance.parameters.add(new Parameter(name, value));
//					return this;
//				}

		public <A> Builder<IN, OUT, OP> preProcessors(List<Class<ProcessingOperationProcessor>> processors) {
			managedInstance.preProcessors = new ArrayList<>(processors);
			return this;
		}

		public <A> Builder<IN, OUT, OP> preProcessor(Class<ProcessingOperationProcessor> processor) {
			managedInstance.preProcessors.add(processor);
			return this;
		}

		public <A> Builder<IN, OUT, OP> postProcessor(Class<ProcessingOperationProcessor> processor) {
			managedInstance.postProcessors.add(processor);
			return this;
		}

		public Builder<IN, OUT, OP> onError(BaseOnError onError) {
			this.managedInstance.onErrors.add(onError);
			return this;
		}

		public UnsafeOperation.Builder<IN, OUT, OP> onError(UnsafeOnError<?> onError) {
			this.managedInstance.onErrors.add(onError.getOnError());
			return new UnsafeOperation.Builder<>(this);
		}

		public Builder<IN, OUT, OP> transformer(Transformer<IN, OUT> transformer) {
			this.managedInstance.transformer = transformer;
			return this;
		}

		public ProcessingOperationDefinition<IN, OUT> build() {
			return managedInstance;
		}

	}

	@FunctionalInterface
	public interface ParamRetriever<T extends Operation<?, ?>, U> {

		Parameter<U> getParameterValue(T operation);

	}

	public static abstract class ParameterModel<OP extends Operation<?, ?>, T> {

		private ParamRetriever<OP, T> paramRetriever;

		public abstract T getValue(Object item);

		private ParameterModel(ParamRetriever<OP, T> paramRetriever) {
			this.paramRetriever = paramRetriever;
		}

		public ParamRetriever<?, ?> getParamRetriever() {
			return paramRetriever;
		}

	}

	public static class ValueParameterModel<OP extends Operation<?, ?>, T> extends ParameterModel<OP, T> {

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

	public static class SupplierParameterModel<OP extends Operation<?, ?>, T> extends ParameterModel<OP, T> {

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

	public static class InterpretationContextParameterModel<OP extends Operation<?, ?>, T>
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

	public static class UnsafeOperation<IN, OUT, OP extends io.github.gear4jtest.core.model.Operation<IN, OUT>> {

		private ProcessingOperationDefinition.Builder<IN, OUT, OP> operation;

		public static class Builder<IN, OUT, OP extends io.github.gear4jtest.core.model.Operation<IN, OUT>> {

			private UnsafeOperation<IN, OUT, OP> managedInstance;

			public Builder(ProcessingOperationDefinition.Builder<IN, OUT, OP> operation) {
				this.managedInstance = new UnsafeOperation<>();
				this.managedInstance.operation = operation;
			}

			public Builder<IN, OUT, OP> onError(BaseOnError onError) {
				this.managedInstance.operation.onError(onError);
				return this;
			}

			public UnsafeOperation.Builder<IN, OUT, OP> onError(UnsafeOnError<?> onError) {
				this.managedInstance.operation.onError(onError.getOnError());
				return this;
			}

			public ProcessingOperationDefinition.Builder<IN, OUT, OP> transformer(Transformer<IN, OUT> transformer) {
				this.managedInstance.operation.transformer(transformer);
				return this.managedInstance.operation;
			}

		}

	}

}
