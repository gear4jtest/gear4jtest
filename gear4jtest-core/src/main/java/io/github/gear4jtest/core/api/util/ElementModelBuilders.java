package io.github.gear4jtest.core.api.util;

import java.util.Map;
import java.util.concurrent.ExecutorService;

import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.AssemblyLine.Configuration;
import io.github.gear4jtest.core.api.behavior.BaseError;
import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.config.EventHandlingDefinition;
import io.github.gear4jtest.core.api.config.EventHandlingDefinition.EventConfiguration;
import io.github.gear4jtest.core.api.config.PersistenceConfiguration;
import io.github.gear4jtest.core.api.station.AbstractStation;
import io.github.gear4jtest.core.api.station.AssemblyLineCallStation;
import io.github.gear4jtest.core.api.station.ContainerBaseStation;
import io.github.gear4jtest.core.api.station.IteratorStation;
import io.github.gear4jtest.core.api.station.IteratorStation.ListAccumulator;
import io.github.gear4jtest.core.api.station.IteratorStation.SetAccumulator;
import io.github.gear4jtest.core.api.station.SequenceStation;
import io.github.gear4jtest.core.api.station.SignalStation;
import io.github.gear4jtest.core.api.station.UnaryIfElseContainerStation;
import io.github.gear4jtest.core.api.station.UnaryWorkStation;
import io.github.gear4jtest.core.api.station.WorkStation;

/**
 * Backward-compatible umbrella facade for the historical Gear4J builder
 * helpers.
 *
 * <p>
 * New code should prefer the thematic facades {@link AssemblyLines},
 * {@link Stations}, {@link Errors} and {@link RuntimeDefinitions}. This class
 * is intentionally kept as a compatibility layer because many generated
 * snippets and examples still import it statically.
 * </p>
 */
public final class ElementModelBuilders {

    private ElementModelBuilders() {
    }

    public static <T> BaseError.UnSafeError.Builder<T> ignore(Class<? extends Throwable> throwableType) {
        return Errors.ignore(throwableType);
    }

    public static <T> BaseError.SafeError.Builder<T> fatal(Class<? extends Throwable> throwableType) {
        return Errors.fatal(throwableType);
    }

    public static <T> BaseError.SafeError.Builder<T> stop(Class<? extends Throwable> throwableType) {
        return Errors.stop(throwableType);
    }

    public static EventConfiguration.Builder eventConfiguration() {
        return RuntimeDefinitions.eventConfiguration();
    }

    public static EventHandlingDefinition.Builder eventHandling() {
        return RuntimeDefinitions.eventHandling();
    }

    public static <T> AssemblyLine.Builder<T, T> createAssemblyLine(String identifier) {
        return AssemblyLines.createAssemblyLine(identifier);
    }

    public static <A, B, T extends Operator<A, B>> WorkStation.Builder<A, B, T> processingOperation(String id,
                                                                                                    Class<T> step) {
        return Stations.processingOperation(id, step);
    }

    public static <A, T extends Operator<A, A>> UnaryWorkStation.Builder<A, T> unaryProcessingOperation(String id,
                                                                                                        Class<T> step) {
        return Stations.unaryProcessingOperation(id, step);
    }

    public static <A> IteratorStation.Builder<A, A> iterate(String id) {
        return Stations.iterate(id);
    }

    public static <T> SignalStation.Builder<T> fatalSignal(Class<T> clazz) {
        return Stations.fatalSignal(clazz);
    }

    public static <U, V> SignalStation.Builder<Map<U, V>> fatalSignal(MapType<U, V> clazz) {
        return Stations.fatalSignal(clazz);
    }

    public static <T> ContainerBaseStation.Builder<T, T> container(Class<T> clazz) {
        return Stations.container(clazz);
    }

    public static <T> ContainerBaseStation.Builder<T, T> container(Class<T> clazz, ExecutorService executorService) {
        return Stations.container(clazz, executorService);
    }

    public static <T> UnaryIfElseContainerStation.Builder<T> ifElseContainer(Class<T> clazz) {
        return Stations.ifElseContainer(clazz);
    }

    public static Configuration.Builder configuration() {
        return RuntimeDefinitions.configuration();
    }

    public static PersistenceConfiguration.Builder persistenceConfiguration() {
        return RuntimeDefinitions.persistenceConfiguration();
    }

    public static <IN, OUT> SequenceStation.Builder<IN, OUT> chain(String id, AbstractStation<IN, OUT> step) {
        return AssemblyLines.chain(id, step);
    }

    public static <IN, OUT> AssemblyLineCallStation.Builder<IN, OUT> assemblyLineCall(String id) {
        return AssemblyLines.assemblyLineCall(id);
    }

    public static <IN, OUT> AssemblyLineCallStation<IN, OUT> inlineAssemblyLine(String id,
                                                                                AssemblyLine<IN, OUT> childAssemblyLine) {
        return AssemblyLines.inlineAssemblyLine(id, childAssemblyLine);
    }

    public static <IN, OUT> AssemblyLineCallStation<IN, OUT> nestedAssemblyLine(String id,
                                                                                AssemblyLine<IN, OUT> childAssemblyLine) {
        return AssemblyLines.nestedAssemblyLine(id, childAssemblyLine);
    }

    public static ListAccumulator toList() {
        return Stations.toList();
    }

    public static SetAccumulator toSet() {
        return Stations.toSet();
    }

    public static class Type<T> extends Stations.Type<T> {
        public Type(Class<T> clazz) {
            super(clazz);
        }
    }

    public static class MapType<U, V> extends Stations.MapType<U, V> {
        public MapType(Class<U> classA, Class<V> classB) {
            super(classA, classB);
        }
    }
}
