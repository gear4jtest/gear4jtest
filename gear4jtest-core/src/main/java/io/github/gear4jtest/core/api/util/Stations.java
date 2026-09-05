package io.github.gear4jtest.core.api.util;

import java.util.Objects;
import java.util.concurrent.ExecutorService;

import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.behavior.SignalType;
import io.github.gear4jtest.core.api.station.AbstractStation;
import io.github.gear4jtest.core.api.station.ContainerBaseStation;
import io.github.gear4jtest.core.api.station.ContainerBranch;
import io.github.gear4jtest.core.api.station.IteratorStation;
import io.github.gear4jtest.core.api.station.IteratorStation.ListAccumulator;
import io.github.gear4jtest.core.api.station.IteratorStation.SetAccumulator;
import io.github.gear4jtest.core.api.station.SignalStation;
import io.github.gear4jtest.core.api.station.UnaryIfElseContainerStation;
import io.github.gear4jtest.core.api.station.UnaryWorkStation;
import io.github.gear4jtest.core.api.station.WorkStation;

/**
 * Builders for station definitions.
 */
public final class Stations {
    private static final String CLAZZ_MUST_NOT_BE_NULL = "clazz must not be null";

    private Stations() {
    }

    public static <A, B, T extends Operator<A, B>> WorkStation.Builder<A, B, T> processingOperation(String id,
                                                                                                    Class<T> step) {
        return new WorkStation.Builder<A, B, T>().id(id).type(step);
    }

    public static <A, T extends Operator<A, A>> UnaryWorkStation.Builder<A, T> unaryProcessingOperation(String id,
                                                                                                        Class<T> step) {
        return new UnaryWorkStation.Builder<A, T>().id(id).type(step);
    }

    public static <A> IteratorStation.Builder<A, A> iterate(String id) {
        return new IteratorStation.Builder<>(id);
    }

    public static <T> SignalStation.Builder<T> fatalSignal(Class<T> clazz) {
        Objects.requireNonNull(clazz, CLAZZ_MUST_NOT_BE_NULL);
        return fatalSignal();
    }

    /**
     * Creates a fatal signal builder when no reifiable class token exists for the
     * payload type, for example
     * {@code Stations.<Map<String, Integer>>fatalSignal()}.
     */
    public static <T> SignalStation.Builder<T> fatalSignal() {
        return new SignalStation.Builder<T>().type(SignalType.FATAL);
    }

    public static <T> ContainerBaseStation.Builder<T, T> container(Class<T> clazz) {
        Objects.requireNonNull(clazz, CLAZZ_MUST_NOT_BE_NULL);
        return new ContainerBaseStation.Builder<>();
    }

    public static <T> ContainerBaseStation.Builder<T, T> container(String id, Class<T> clazz) {
        Objects.requireNonNull(clazz, CLAZZ_MUST_NOT_BE_NULL);
        return new ContainerBaseStation.Builder<T, T>().id(id);
    }

    public static <T> ContainerBaseStation.Builder<T, T> container(Class<T> clazz, ExecutorService executorService) {
        Objects.requireNonNull(clazz, CLAZZ_MUST_NOT_BE_NULL);
        return new ContainerBaseStation.Builder<>(executorService);
    }

    public static <T> ContainerBaseStation.Builder<T, T> container(String id,
                                                                   Class<T> clazz,
                                                                   ExecutorService executorService) {
        Objects.requireNonNull(clazz, CLAZZ_MUST_NOT_BE_NULL);
        return new ContainerBaseStation.Builder<T, T>(executorService).id(id);
    }

    public static <IN, OUT> ContainerBranch<IN, OUT> branch(String id, AbstractStation<IN, OUT> station) {
        return ContainerBranch.of(id, station);
    }

    public static <T> UnaryIfElseContainerStation.Builder<T> ifElseContainer(Class<T> clazz) {
        Objects.requireNonNull(clazz, CLAZZ_MUST_NOT_BE_NULL);
        return new UnaryIfElseContainerStation.Builder<>();
    }

    public static <T> UnaryIfElseContainerStation.Builder<T> ifElseContainer(String id, Class<T> clazz) {
        Objects.requireNonNull(clazz, CLAZZ_MUST_NOT_BE_NULL);
        return new UnaryIfElseContainerStation.Builder<T>().id(id);
    }

    public static ListAccumulator toList() {
        return new ListAccumulator();
    }

    public static SetAccumulator toSet() {
        return new SetAccumulator();
    }

    public static class Type<T> {
        private final Class<T> clazz;

        public Type(Class<T> clazz) {
            this.clazz = clazz;
        }

        public Class<T> getClazz() {
            return clazz;
        }
    }
}
