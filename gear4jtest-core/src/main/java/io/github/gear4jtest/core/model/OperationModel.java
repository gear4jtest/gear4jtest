package io.github.gear4jtest.core.model;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import io.github.gear4jtest.core.internal.StepLineElement;
import io.github.gear4jtest.core.processor.operation.ChainContextInjector.ChainContext;
import io.github.gear4jtest.core.processor.operation.OperationParamsInjector.ParameterValue;

public class OperationModel<IN, OUT> {

	private Supplier<Operation<IN, OUT>> handler;

	private List<Parameter<?, ?>> parameters;
	
	private ChainContextRetriever<?> contextRetriever;
	
	private List<ProcessorModel<StepLineElement>> preProcessors;
	
	private List<ProcessorModel<StepLineElement>> postProcessors;
	
	private List<OnError> onErrors;

	private OperationModel() {
		this.parameters = new ArrayList<>();
	}

	public Supplier<Operation<IN, OUT>> getHandler() {
		return handler;
	}
	
	public List<Parameter<?, ?>> getParameters() {
		return parameters;
	}
	
	public ChainContextRetriever<?> getContextRetriever() {
		return contextRetriever;
	}

	public List<ProcessorModel<StepLineElement>> getPreProcessors() {
		return preProcessors;
	}

	public List<ProcessorModel<StepLineElement>> getPostProcessors() {
		return postProcessors;
	}
	
	public List<OnError> getOnErrors() {
		return onErrors;
	}

	public static class Builder<IN, OUT, OP extends Operation<IN, OUT>> {

		private final OperationModel<IN, OUT> managedInstance;

		Builder() {
			managedInstance = new OperationModel<>();
		}

		public <A, T extends Operation<IN, A>> Builder<IN, A, T> handler(Supplier<T> handler) {
			managedInstance.handler = (Supplier) handler;
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
		
		public <A> Builder<IN, OUT, OP> preProcessors(List<ProcessorModel<StepLineElement>> processors) {
			managedInstance.preProcessors = new ArrayList<>(processors);
			return this;
		}
		
		public <A> Builder<IN, OUT, OP> preProcessor(ProcessorModel<StepLineElement> processor) {
			managedInstance.preProcessors.add(processor);
			return this;
		}
		
		public <A> Builder<IN, OUT, OP> postProcessor(ProcessorModel<StepLineElement> processor) {
			managedInstance.postProcessors.add(processor);
			return this;
		}
		
		public Builder<IN, OUT, OP> onError(OnError onError) {
			this.managedInstance.onErrors.add(onError);
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

}