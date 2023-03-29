package io.github.gear4jtest.core.model;

import java.util.ArrayList;
import java.util.List;

import io.github.gear4jtest.core.processor.PostProcessor;
import io.github.gear4jtest.core.processor.PreProcessor;
import io.github.gear4jtest.core.processor.Transformer;
import io.github.gear4jtest.core.processor.operation.ChainContextInjector.ChainContext;
import io.github.gear4jtest.core.processor.operation.OperationParamsInjector.ParameterValue;

public class OperationModel<IN, OUT> {

	private Class<Operation<IN, OUT>> type;

	private List<Parameter<?, ?>> parameters;
	
	private ChainContextRetriever<?> contextRetriever;
	
	private List<Class<? extends PreProcessor>> preProcessors;
	
	private List<Class<? extends PostProcessor>> postProcessors;
	
	private List<BaseOnError> onErrors;

	private Transformer<IN, OUT> transformer;

	private OperationModel() {
		this.parameters = new ArrayList<>();
		this.onErrors = new ArrayList<>();
	}

	public Class<Operation<IN, OUT>> getType() {
		return type;
	}
	
	public List<Parameter<?, ?>> getParameters() {
		return parameters;
	}
	
	public ChainContextRetriever<?> getContextRetriever() {
		return contextRetriever;
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
		
		public <A> Builder<IN, OUT, OP> parameter(Parameter<OP, A> parameter) {
			managedInstance.parameters.add(parameter);
			return this;
		}
		
		public <A> Builder<IN, OUT, OP> context(ChainContextRetriever<OP> contextRetriever) {
			managedInstance.contextRetriever = contextRetriever;
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
		
		public <A> Builder<IN, OUT, OP> preProcessors(List<Class<PreProcessor>> processors) {
			managedInstance.preProcessors = new ArrayList<>(processors);
			return this;
		}
		
		public <A> Builder<IN, OUT, OP> preProcessor(Class<PreProcessor> processor) {
			managedInstance.preProcessors.add(processor);
			return this;
		}
		
		public <A> Builder<IN, OUT, OP> postProcessor(Class<PostProcessor> processor) {
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
		
		ParameterValue<U> getParameterValue(T operation);
		
	}

	public static class Parameter<OP extends Operation<?, ?>, T> {

		private ParamRetriever<OP, T> paramRetriever;
		private T value;
		private String expression;
		private String evaluator;

		public Parameter(ParamRetriever<OP, T> paramRetriever) {
			this.paramRetriever = paramRetriever;
		}

		public Parameter(ParamRetriever<OP, T> paramRetriever, T value) {
			this.paramRetriever = paramRetriever;
			this.value = value;
		}
		
		public Parameter(ParamRetriever<OP, T> paramRetriever, String expression, String evaluator) {
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
		
		public Parameter<OP, T> value(T value) {
			this.value = value;
			return this;
		}
		
	}
	
	@FunctionalInterface
	public interface ChainContextRetriever<T extends Operation<?, ?>> {
		
		ChainContext getParameterValue(T operation);
		
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