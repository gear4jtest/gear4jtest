package io.github.gear4jtest.core.model;

import java.util.List;
import java.util.function.Supplier;

import io.github.gear4jtest.core.internal.LineElement;
import io.github.gear4jtest.core.model.OperationModel.ParamRetriever;
import io.github.gear4jtest.core.model.OperationModel.Parameter;
import io.github.gear4jtest.core.processor.Processor;

public final class ElementModelBuilders {
	
	private ElementModelBuilders() {}
	
	public static <A> ChainModel.Builder<A, ?> chain() {
		return new ChainModel.Builder<>();
	}
	
	public static <A> ChainModel.Builder<A, ?> chain(Class<A> clazz) {
		return new ChainModel.Builder<>();
	}
	
	public static <A> ChainModel.ChainDefaultConfiguration.Builder chainDefaultConfiguration() {
		return new ChainModel.ChainDefaultConfiguration.Builder();
	}
	
	public static <A> ChainModel.StepLineElementDefaultConfiguration.Builder stepLineElementDefaultConfiguration() {
		return new ChainModel.StepLineElementDefaultConfiguration.Builder();
	}
	
	public static <A> OperationModel.Builder<A, ?, ?> operation() {
		return new OperationModel.Builder<>();
	}
	
	public static <A, B, T extends Operation<A, B>> OperationModel.Builder<A, B, T> operation(Supplier<T> step) {
		return new OperationModel.Builder<A, B, T>()
				.handler(step);
	}
	
	public static <A> BranchesModel.Builder<A, List<Object>> branches() {
		return new BranchesModel.Builder<>();
	}
	
	public static <A> BranchesModel.Builder<A, List<Object>> branches(Class<A> clazz) {
		return new BranchesModel.Builder<>();
	}
	
	public static <A> BranchModel.Builder<A, A> branch() {
		return new BranchModel.Builder<>();
	}
	
	public static <A> BranchModel.Builder<A, A> branch(Class<A> clazz) {
		return new BranchModel.Builder<>();
	}
	
	public static <A extends Operation<?, ?>, B> Parameter<A, B> newParameter(ParamRetriever<A, B> name) {
		Parameter<A, B> param = new Parameter<>(name);
		return param;
	}
	
	public static <T extends LineElement> ProcessorModel.Builder<T> processor(Processor<T> processor) {
		return new ProcessorModel.Builder<T>()
				.processor(processor);
	}
	
	public static OnError.Builder onError() {
		return new OnError.Builder();
	}
	
}
