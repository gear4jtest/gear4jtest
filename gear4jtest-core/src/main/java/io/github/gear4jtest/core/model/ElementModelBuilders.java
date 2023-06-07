package io.github.gear4jtest.core.model;

import java.util.List;
import java.util.function.Supplier;

import io.github.gear4jtest.core.internal.LineElement;
import io.github.gear4jtest.core.model.EventHandling.EventConfiguration;
import io.github.gear4jtest.core.model.OperationModel.ParamRetriever;
import io.github.gear4jtest.core.model.OperationModel.ParameterModel;
import io.github.gear4jtest.core.processor.PostProcessor;
import io.github.gear4jtest.core.processor.PreProcessor;
import io.github.gear4jtest.core.processor.Invoker;
import io.github.gear4jtest.core.processor.Processor;

public final class ElementModelBuilders {

	private ElementModelBuilders() {
	}

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

	public static <A, B, T extends Operation<A, B>> OperationModel.Builder<A, B, T> operation(Class<T> step) {
		return new OperationModel.Builder<A, B, T>().type(step);
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

	public static <A extends Operation<?, ?>, B> ParameterModel<A, B> newParameter(
			ParamRetriever<A, B> paramRetriever) {
		ParameterModel<A, B> param = new ParameterModel<>(paramRetriever);
		return param;
	}

	public static <T extends LineElement, V> ProcessorModel.Builder<T> processor(Supplier<Processor> processor) {
		return new ProcessorModel.Builder<T>().processor(processor);
	}

	public static OnError.Builder onError() {
		return new OnError.Builder();
	}

	public static PreProcessorOnError.Builder preOnError(Class<? extends PreProcessor<?>> processor) {
		return new PreProcessorOnError.Builder().processor(processor);
	}

	public static PostProcessorOnError.Builder postOnError(Class<? extends PostProcessor<?>> processor) {
		return new PostProcessorOnError.Builder().processor(processor);
	}

	public static ProcessingProcessorOnError.Builder onProcessingError(Class<? extends Invoker> processor) {
		return new ProcessingProcessorOnError.Builder().processor(processor);
	}

	public static Rule.Builder rule() {
		return new Rule.Builder();
	}

	public static Rule.Builder rule(Class<? extends Exception> clazz) {
		return new Rule.Builder().type(clazz);
	}

	public static IgnoreRule.Builder ignoreRule(Class<? extends Exception> clazz) {
		return new IgnoreRule.Builder().type(clazz);
	}

	public static ChainBreakRule.Builder chainBreakRule(Class<? extends Exception> clazz) {
		return new ChainBreakRule.Builder().type(clazz);
	}

	public static Queue.Builder queue() {
		return new Queue.Builder();
	}

	public static EventConfiguration.Builder eventConfiguration() {
		return new EventConfiguration.Builder();
	}

	public static EventHandling.Builder eventHandling() {
		return new EventHandling.Builder();
	}

}
