package io.github.gear4jtest.core.model;

import java.lang.reflect.ParameterizedType;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import io.github.gear4jtest.core.model.EventHandlingDefinition.EventConfiguration;
import io.github.gear4jtest.core.model.refactor.AbstractOperationDefinition;
import io.github.gear4jtest.core.model.refactor.OperationChain;
import io.github.gear4jtest.core.model.refactor.PipelineOperation;
import io.github.gear4jtest.core.model.refactor.SimpleEventBus;
import io.github.gear4jtest.core.model.refactor.AssemblyLineDefinition;
import io.github.gear4jtest.core.model.refactor.AssemblyLineDefinition.Configuration;
import io.github.gear4jtest.core.model.refactor.BaseError;
import io.github.gear4jtest.core.model.refactor.ContainerBaseDefinition;
import io.github.gear4jtest.core.model.refactor.EventBus;
import io.github.gear4jtest.core.model.refactor.IteratorDefinition;
import io.github.gear4jtest.core.model.refactor.IteratorDefinition.ListAccumulator;
import io.github.gear4jtest.core.model.refactor.IteratorDefinition.SetAccumulator;
import io.github.gear4jtest.core.model.refactor.OperationConfigurationDefinition;
import io.github.gear4jtest.core.model.refactor.PersistenceConfiguration;
import io.github.gear4jtest.core.model.refactor.ProcessingOperationDefinition;
import io.github.gear4jtest.core.model.refactor.SignalDefiinition;
import io.github.gear4jtest.core.model.refactor.SignalType;
import io.github.gear4jtest.core.model.refactor.Transformer;
import io.github.gear4jtest.core.model.refactor.UnaryProcessingOperationDefinition;
import io.github.gear4jtest.core.model.refactor.UnaryIfElseContainerDefinition;

public final class ElementModelBuilders {

	private ElementModelBuilders() {
	}

	public static <T> BaseError.UnSafeError.Builder<T> ignore(Class<? extends Throwable> throwableType) {
		return new BaseError.UnSafeError.Builder<>(SignalType.IGNORE, throwableType);
	}

	public static <T> BaseError.SafeError.Builder<T> fatal(Class<? extends Throwable> throwableType) {
		return new BaseError.SafeError.Builder<>(SignalType.FATAL, throwableType);
	}

	public static <T> BaseError.SafeError.Builder<T> stop(Class<? extends Throwable> throwableType) {
		return new BaseError.SafeError.Builder<>(SignalType.STOP, throwableType);
	}

	public static SimpleEventBus.Builder simpleBus(String name) {
		return new SimpleEventBus.Builder().id(name);
	}

	public static EventConfiguration.Builder eventConfiguration() {
		return new EventConfiguration.Builder();
	}

	public static EventHandlingDefinition.Builder eventHandling() {
		return new EventHandlingDefinition.Builder();
	}

	public static <T, U> MapType<T, U> typeMap(Class<T> clazzA, Class<U> classB) {
		return new MapType<>(clazzA, classB);
	}

	public static <T> AssemblyLineDefinition.Builder<T, T> createAssemblyLine(String identifier) {
		return AssemblyLineDefinition.builder(identifier);
	}

	public static <A, B, T extends Transformer<A, B>> ProcessingOperationDefinition.Builder<A, B, T> processingOperation(String id, Class<T> step) {
		return new ProcessingOperationDefinition.Builder<A, B, T>().id(id).type(step);
	}

	public static <A, T extends Transformer<A, A>> UnaryProcessingOperationDefinition.Builder<A, T> unaryProcessingOperation(String id, Class<T> step) {
		return new UnaryProcessingOperationDefinition.Builder<A, T>().id(id).type(step);
	}

	public static <A> IteratorDefinition.Builder<A, A> iterate(String id) {
		return new IteratorDefinition.Builder<>(id);
	}

	public static <T> SignalDefiinition.Builder<T> fatalSignal(Class<T> clazz) {
		return new SignalDefiinition.Builder<T>()
				.type(SignalType.FATAL);
	}

	public static <U, V> SignalDefiinition.Builder<Map<U, V>> fatalSignal(MapType<U, V> clazz) {
		return new SignalDefiinition.Builder<Map<U, V>>()
				.type(SignalType.FATAL);
	}

	public static <T> ContainerBaseDefinition.Builder<T, T> container(Class<T> clazz) {
		return new ContainerBaseDefinition.Builder<>();
	}

	public static <T> ContainerBaseDefinition.Builder<T, T> container(Class<T> clazz, ExecutorService executorService) {
		return new ContainerBaseDefinition.Builder<>(executorService);
	}

	public static <T> UnaryIfElseContainerDefinition.Builder<T> ifElseContainer(Class<T> clazz) {
		return new UnaryIfElseContainerDefinition.Builder<>();
	}

	public static Configuration.Builder configuration() {
		return new Configuration.Builder();
	}

	public static OperationConfigurationDefinition.Builder operationConfiguration() {
		return new OperationConfigurationDefinition.Builder();
	}

	public static PersistenceConfiguration.Builder persistenceConfiguration() {
		return new PersistenceConfiguration.Builder();
	}

	public static <T extends EventBus> PersistenceConfiguration.Builder customEventBus(Class<T> clazz) {
		return new PersistenceConfiguration.Builder();
	}

	public static <T extends SimpleEventBus> PersistenceConfiguration.Builder eventBus(Class<T> clazz) {
		return new PersistenceConfiguration.Builder();
	}

	public static <IN, OUT> OperationChain.Builder<IN, OUT> chain(AbstractOperationDefinition<IN, OUT> step) {
		return new OperationChain.Builder<>(step);
	}

	public static <IN, OUT> PipelineOperation.Builder<IN, OUT> pipelineOperation(String id,
																				 AssemblyLineDefinition<IN, OUT> subPipeline) {
		return new PipelineOperation.Builder<>(id, subPipeline);
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

	public static abstract class TypeReference<T> {
		protected final java.lang.reflect.Type _type;
		public TypeReference() {
			java.lang.reflect.Type superClass = getClass().getGenericSuperclass();
			if (superClass instanceof Class<?>) { // sanity check, should never happen
				throw new IllegalArgumentException("Internal error: TypeReference constructed without actual type information");
			}
			/* 22-Dec-2008, tatu: Not sure if this case is safe -- I suspect
			 *   it is possible to make it fail?
			 *   But let's deal with specific
			 *   case when we know an actual use case, and thereby suitable
			 *   workarounds for valid case(s) and/or error to throw
			 *   on invalid one(s).
			 */
			_type = ((ParameterizedType) superClass).getActualTypeArguments()[0];
			System.out.println(this.getClass());
//			ParameterizedType type = this.getClass();
//			java.lang.reflect.Type[] argumentsTypes = type.getActualTypeArguments();
		}
	}
}
