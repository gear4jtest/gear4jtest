package io.github.gear4jtest.core.model;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import io.github.gear4jtest.core.internal.LineElement;
import io.github.gear4jtest.core.model.EventHandlingDefinition.EventConfiguration;
import io.github.gear4jtest.core.model.OperationModel.InterpretationContextParameterModel;
import io.github.gear4jtest.core.model.OperationModel.InterpretationContextParameterModel.InterpretationContext;
import io.github.gear4jtest.core.model.OperationModel.ParamRetriever;
import io.github.gear4jtest.core.model.OperationModel.ParameterModel;
import io.github.gear4jtest.core.model.OperationModel.SupplierParameterModel;
import io.github.gear4jtest.core.model.OperationModel.ValueParameterModel;
import io.github.gear4jtest.core.model.refactor.AssemblyLineDefinition;
import io.github.gear4jtest.core.model.refactor.AssemblyLineDefinition.Configuration;
import io.github.gear4jtest.core.model.refactor.ContainerDefinition;
import io.github.gear4jtest.core.model.refactor.IteratorDefinition.Accumulator;
import io.github.gear4jtest.core.model.refactor.IteratorDefinition.ListAccumulator;
import io.github.gear4jtest.core.model.refactor.IteratorDefinition.SetAccumulator;
import io.github.gear4jtest.core.model.refactor.LineDefinition;
import io.github.gear4jtest.core.model.refactor.OperationConfigurationDefinition;
import io.github.gear4jtest.core.model.refactor.ProcessingOperationDefinition;
import io.github.gear4jtest.core.model.refactor.SignalDefiinition;
import io.github.gear4jtest.core.model.refactor.SignalDefiinition.SignalType;
import io.github.gear4jtest.core.model.refactor.StartingPointDefinition;
import io.github.gear4jtest.core.model.refactor.StopSignalDefiinition;
import io.github.gear4jtest.core.processor.ProcessingOperationProcessor;

public final class ElementModelBuilders {

	private ElementModelBuilders() {
	}

	public static <A> ChainModel.Builder<A, ?> chain() {
		return new ChainModel.Builder<>();
	}

	public static <A> ChainModel.Builder<A, ?> chain(Class<A> clazz) {
		return new ChainModel.Builder<>();
	}

	public static <T> StartingPointModel.Builder<T, T> startingPoint(Class<T> inputClass) {
		return new StartingPointModel.Builder<T, T>();
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

	public static <A extends Operation<?, ?>, B> ParameterModel<A, B> newParameter(ParamRetriever<A, B> paramRetriever,
			B value) {
		return new ValueParameterModel<>(paramRetriever, value);
	}

	public static <A extends Operation<?, ?>, B> ParameterModel<A, B> newParameter(ParamRetriever<A, B> paramRetriever,
			Supplier<B> value) {
		return new SupplierParameterModel<>(paramRetriever, value);
	}

	public static <A extends Operation<?, ?>, B> ParameterModel<A, B> newParameter(ParamRetriever<A, B> paramRetriever,
			Function<InterpretationContext, B> value) {
		return new InterpretationContextParameterModel<>(paramRetriever, value);
	}

	public static <T extends LineElement, V> ProcessorModel.Builder<T> processor(Supplier<ProcessingOperationProcessor> processor) {
		return new ProcessorModel.Builder<T>().processor(processor);
	}

	public static OnError.Builder onError() {
		return new OnError.Builder();
	}

	public static PreProcessorOnError.Builder preOnError(Class<? extends ProcessingOperationProcessor<?>> processor) {
		return new PreProcessorOnError.Builder().processor(processor);
	}

	public static PostProcessorOnError.Builder postOnError(Class<? extends ProcessingOperationProcessor<?>> processor) {
		return new PostProcessorOnError.Builder().processor(processor);
	}

	public static ProcessingProcessorOnError.Builder onProcessingError() {
		return new ProcessingProcessorOnError.Builder();
	}

	public static GlobalOnError.Builder globalOnError() {
		return new GlobalOnError.Builder();
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

	public static EventHandlingDefinition.Builder eventHandling() {
		return new EventHandlingDefinition.Builder();
	}

	public static <T> SignalModel.Builder<T> signal(Class<T> clazz) {
		return new SignalModel.Builder<>();
	}

	public static <U, V> SignalModel.Builder<Map<U, V>> signal(MapType<U, V> clazz) {
		return new SignalModel.Builder<>();
	}

	public static <T> ContainerModel.Builder<T, T> container() {
		return new ContainerModel.Builder<>();
	}

	public static <T, U> MapType<T, U> typeMap(Class<T> clazzA, Class<U> classB) {
		return new MapType<>(clazzA, classB);
	}

	public static AssemblyLineDefinition.Builder<?, ?> asssemblyLineDefinition(String identifier) {
		return new AssemblyLineDefinition.Builder<>(identifier);
	}

	public static <START, END> AssemblyLineDefinition.Builder<START, END> asssemblyLineDefinition(String identifier, LineDefinition<START, END> line) {
		return new AssemblyLineDefinition.Builder<>(identifier, line);
	}

	public static <START> LineDefinition.Builder<START, START> line(StartingPointDefinition<START> startingPoint) {
		return new LineDefinition.Builder<>(startingPoint);
	}

	public static <A, B, T extends Operation<A, B>> ProcessingOperationDefinition.Builder<A, B, T> processingOperation(Class<T> step) {
		return new ProcessingOperationDefinition.Builder<A, B, T>().type(step);
	}
//
//	public static <T> SignalModel.Builder<T> signall(Class<T> clazz) {
//		return new SignalModel.Builder<>();
//	}
//
//	public static <U, V> SignalDefiinition.Builder<Map<U, V>> signall(MapType<U, V> clazz) {
//		return new SignalDefiinition.Builder<>();
//	}

	public static <T> StopSignalDefiinition.Builder<T> stopSignal(Class<T> clazz) {
		return new StopSignalDefiinition.Builder<T>()
				.type(SignalType.STOP);
	}

	public static <U, V> StopSignalDefiinition.Builder<Map<U, V>> stopSignal(MapType<U, V> clazz) {
		return new StopSignalDefiinition.Builder<Map<U, V>>()
				.type(SignalType.STOP);
	}

	public static <T> SignalDefiinition.Builder<T> fatalSignal(Class<T> clazz) {
		return new SignalDefiinition.Builder<T>()
				.type(SignalType.FATAL);
	}

	public static <U, V> SignalDefiinition.Builder<Map<U, V>> fatalSignal(MapType<U, V> clazz) {
		return new SignalDefiinition.Builder<Map<U, V>>()
				.type(SignalType.FATAL);
	}

	public static <T> ContainerDefinition.Builder<T, T> containerr() {
		return new ContainerDefinition.Builder<>();
	}

	public static <T> StartingPointDefinition<T> startingPointt(Class<T> clazz) {
		return new StartingPointDefinition<>(clazz);
	}

	public static Configuration.Builder configuration() {
		return new Configuration.Builder();
	}

	public static OperationConfigurationDefinition.Builder operationConfiguration() {
		return new OperationConfigurationDefinition.Builder();
	}

	public static ListAccumulator toList() {
		return new ListAccumulator();
	}

	public static SetAccumulator toSet() {
		return new SetAccumulator();
	}

	public static class Type<T> {
		
		private Class<T> clazz;
		
		public Type(Class<T> clazz) {
			this.clazz = clazz;
		}
		
	}

	public static class MapType<U, V> extends Type<Map> {
		
		private Class<U> classA;
		private Class<V> classB;
		
		public MapType(Class<U> classA, Class<V> classB) {
			super(Map.class);
			this.classA = classA;
			this.classB = classB;
		}
		
	}
}
