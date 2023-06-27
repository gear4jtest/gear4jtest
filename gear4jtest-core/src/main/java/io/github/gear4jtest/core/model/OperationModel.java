package io.github.gear4jtest.core.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.gear4jtest.core.processor.PostProcessor;
import io.github.gear4jtest.core.processor.PreProcessor;
import io.github.gear4jtest.core.processor.Processor;
import io.github.gear4jtest.core.processor.Transformer;
import io.github.gear4jtest.core.processor.operation.OperationParamsInjector.Parameter;

public class OperationModel<IN, OUT> {

	private Class<Operation<IN, OUT>> type;

	private List<ParameterModel<?, ?>> parameters;

	private List<Class<? extends PreProcessor>> preProcessors;

	private List<Class<? extends PostProcessor>> postProcessors;

	private List<BaseOnError> onErrors;

	private Transformer<IN, OUT> transformer;

	private Map<Class<? extends Processor>, Object> processorModels;

	private OperationModel() {
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

	public List<Class<? extends PreProcessor>> getPreProcessors() {
		return preProcessors;
	}

	public List<Class<? extends PostProcessor>> getPostProcessors() {
		return postProcessors;
	}

	public List<BaseOnError> getOnErrors() {
		return onErrors;
	}

	public Transformer<IN, OUT> getTransformer() {
		return transformer;
	}

	public Map<Class<? extends Processor>, Object> getProcessorModels() {
		return processorModels;
	}

	public static class Builder<IN, OUT, OP extends Operation<IN, OUT>> {

		private final OperationModel<IN, OUT> managedInstance;

		Builder() {
			managedInstance = new OperationModel<>();
		}

		public <A, T extends Operation<IN, A>> Builder<IN, A, T> type(Class<T> type) {
			managedInstance.type = (Class) type;
			return (Builder<IN, A, T>) this;
		}

//		public Builder<IN, OUT> parameter(String name, String evaluator, String value) {
//			managedInstance.parameters.add(new Parameter(name, value, evaluator));
//			return this;
//		}

//		public <A> Builder<IN, OUT> parameter(ParamRetriever<A> name, String evaluator, String value) {
//			managedInstance.parameters.add(new Parameter("", value, evaluator));
//			return this;
//		}
//
//		public <A> Builder<IN, OUT> parameter(ParamRetriever<A> name, String value) {
//			managedInstance.parameters.add(new Parameter("", value, evaluator));
//			return this;
//		}

//		public <A> Builder<IN, OUT, OP> parameter(ParameterModel<OP, A> parameter) {
//			managedInstance.parameters.add(parameter);
//			return this;
//		}

		public <A> Builder<IN, OUT, OP> processorModel(Class<? extends Processor<A>> processor, A model) {
			managedInstance.processorModels.put(processor, model);
			return this;
		}

//		public <A, B> Parameter<B> newParameter(ParamRetriever<A, B> name) {
//			Parameter<B> param = new Parameter<>(name);
//			return param;
//		}

//		public Builder<IN, OUT> parameter(String name, Object value) {
//			managedInstance.parameters.add(new Parameter(name, value));
//			return this;
//		}

		public <A> Builder<IN, OUT, OP> preProcessors(List<Class<PreProcessor<?>>> processors) {
			managedInstance.preProcessors = new ArrayList<>(processors);
			return this;
		}

		public <A> Builder<IN, OUT, OP> preProcessor(Class<PreProcessor<?>> processor) {
			managedInstance.preProcessors.add(processor);
			return this;
		}

		public <A> Builder<IN, OUT, OP> postProcessor(Class<PostProcessor<?>> processor) {
			managedInstance.postProcessors.add(processor);
			return this;
		}

		public Builder<IN, OUT, OP> onError(BaseOnError onError) {
			this.managedInstance.onErrors.add(onError);
			return this;
		}

		public OperationModel.UnsafeOperationModel.Builder<IN, OUT, OP> onError(UnsafeOnError<?> onError) {
			this.managedInstance.onErrors.add(onError.getOnError());
			return new UnsafeOperationModel.Builder<>(this);
		}

		public Builder<IN, OUT, OP> transformer(Transformer<IN, OUT> transformer) {
			this.managedInstance.transformer = transformer;
			return this;
		}

		public OperationModel<IN, OUT> build() {
			return managedInstance;
		}

	}

	@FunctionalInterface
	public interface ParamRetriever<T extends Operation<?, ?>, U> {

		Parameter<U> getParameterValue(T operation);

	}

	public static class ParameterModel<OP extends Operation<?, ?>, T> {

		private ParamRetriever<OP, T> paramRetriever;
		private T value;
//		private Supplier<T> valueSupplier;
//		private Function<InterpretationContext, T> valueFunction;
		private String expression;
		private String evaluator;

		public ParameterModel(ParamRetriever<OP, T> paramRetriever) {
			this.paramRetriever = paramRetriever;
		}

		public ParameterModel(ParamRetriever<OP, T> paramRetriever, T value) {
			this.paramRetriever = paramRetriever;
			this.value = value;
		}

		public ParameterModel(ParamRetriever<OP, T> paramRetriever, String expression, String evaluator) {
			this.paramRetriever = paramRetriever;
			this.expression = expression;
			this.evaluator = evaluator;
		}

		public ParamRetriever<?, ?> getParamRetriever() {
			return paramRetriever;
		}

		public T getValue() {
			return value;
		}

		public String getEvaluator() {
			return evaluator;
		}

		public String getExpression() {
			return expression;
		}

		public ParameterModel<OP, T> value(T value) {
			this.value = value;
			return this;
		}

	}

	public static class UnsafeOperationModel<IN, OUT, OP extends Operation<IN, OUT>> {

		private OperationModel.Builder<IN, OUT, OP> operation;

		public static class Builder<IN, OUT, OP extends Operation<IN, OUT>> {

			private UnsafeOperationModel<IN, OUT, OP> managedInstance;

			public Builder(OperationModel.Builder<IN, OUT, OP> operation) {
				this.managedInstance = new UnsafeOperationModel<>();
				this.managedInstance.operation = operation;
			}

			public Builder<IN, OUT, OP> onError(BaseOnError onError) {
				this.managedInstance.operation.onError(onError);
				return this;
			}

			public OperationModel.UnsafeOperationModel.Builder<IN, OUT, OP> onError(UnsafeOnError<?> onError) {
				this.managedInstance.operation.onError(onError.getOnError());
				return this;
			}

			public OperationModel.Builder<IN, OUT, OP> transformer(Transformer<IN, OUT> transformer) {
				this.managedInstance.operation.transformer(transformer);
				return this.managedInstance.operation;
			}

		}

	}

}
