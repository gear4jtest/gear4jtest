package io.github.gear4jtest.core.model;

import java.lang.reflect.ParameterizedType;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import io.github.gear4jtest.core.model.EventHandlingDefinition.EventConfiguration;
import io.github.gear4jtest.core.model.AssemblyLine.Configuration;
import io.github.gear4jtest.core.model.IteratorStation.ListAccumulator;
import io.github.gear4jtest.core.model.IteratorStation.SetAccumulator;

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

	public static <T> AssemblyLine.Builder<T, T> createAssemblyLine(String identifier) {
		return AssemblyLine.builder(identifier);
	}

	public static <A, B, T extends Operator<A, B>> WorkStation.Builder<A, B, T> processingOperation(String id, Class<T> step) {
		return new WorkStation.Builder<A, B, T>().id(id).type(step);
	}

	public static <A, T extends Operator<A, A>> UnaryWorkStation.Builder<A, T> unaryProcessingOperation(String id, Class<T> step) {
		return new UnaryWorkStation.Builder<A, T>().id(id).type(step);
	}

	public static <A> IteratorStation.Builder<A, A> iterate(String id) {
		return new IteratorStation.Builder<>(id);
	}

	public static <T> SignalStation.Builder<T> fatalSignal(Class<T> clazz) {
		return new SignalStation.Builder<T>()
				.type(SignalType.FATAL);
	}

	public static <U, V> SignalStation.Builder<Map<U, V>> fatalSignal(MapType<U, V> clazz) {
		return new SignalStation.Builder<Map<U, V>>()
				.type(SignalType.FATAL);
	}

	public static <T> ContainerBaseStation.Builder<T, T> container(Class<T> clazz) {
		return new ContainerBaseStation.Builder<>();
	}

	public static <T> ContainerBaseStation.Builder<T, T> container(Class<T> clazz, ExecutorService executorService) {
		return new ContainerBaseStation.Builder<>(executorService);
	}

	public static <T> UnaryIfElseContainerStation.Builder<T> ifElseContainer(Class<T> clazz) {
		return new UnaryIfElseContainerStation.Builder<>();
	}

	public static Configuration.Builder configuration() {
		return new Configuration.Builder();
	}

	public static StationConfigurationDefinition.Builder operationConfiguration() {
		return new StationConfigurationDefinition.Builder();
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

	public static <IN, OUT> SequenceStation.Builder<IN, OUT> chain(String id, AbstractStation<IN, OUT> step) {
		return SequenceStation.Builder.<IN>create(id)
                .next(step);
	}

//	public static <IN, OUT> PipelineOperation.Builder<IN, OUT> pipelineOperation(String id,
//																				 AssemblyLine<IN, OUT> subPipeline) {
//		return new PipelineOperation.Builder<>(id, subPipeline);
//	}

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
