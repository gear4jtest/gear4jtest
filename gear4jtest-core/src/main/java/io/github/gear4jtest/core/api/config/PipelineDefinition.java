package io.github.gear4jtest.core.api.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.station.AbstractStation;
import io.github.gear4jtest.core.api.station.StationKind;

public class PipelineDefinition<IN, OUT> extends AbstractStation<IN, OUT> {
    private Function<IN, ? extends Iterable<?>> func;
    private AssemblyLine<?, ?> assemblyLine;
    private Accumulator accumulator;
    private Collector<?, ?, ?> collector;

    private PipelineDefinition() {
        super("", StationKind.OTHER);
    }

    private PipelineDefinition(Function<IN, ? extends Iterable<?>> func,
                               AssemblyLine<?, ?> assemblyLine,
                               Accumulator accumulator,
                               Collector<?, ?, ?> collector) {
        this();
        this.func = func;
        this.assemblyLine = assemblyLine;
        this.accumulator = accumulator;
        this.collector = collector;
    }

    public Function<IN, ? extends Iterable<?>> getFunc() {
        return func;
    }

    public AssemblyLine<?, ?> getAssemblyLine() {
        return assemblyLine;
    }

    public Accumulator getAccumulator() {
        return accumulator;
    }

    public Collector<?, ?, ?> getCollector() {
        return collector;
    }

    public static class Builder<IN, OUT> {
        private Function<IN, ? extends Iterable<?>> func;
        private AssemblyLine<?, ?> assemblyLine;
        private Accumulator accumulator;
        private Collector<?, ?, ?> collector;

        public Builder() {
        }

        private <PREVIOUS_OUT> Builder(Builder<IN, PREVIOUS_OUT> source) {
            this.func = source.func;
            this.assemblyLine = source.assemblyLine;
            this.accumulator = source.accumulator;
            this.collector = source.collector;
        }

        public <A> Builder<IN, A> iterableFunction(Function<IN, ? extends Iterable<A>> func) {
            this.func = func;
            return new Builder<>(this);
        }

        public <A> Builder<IN, A> pipeline(AssemblyLine<OUT, A> assemblyLine) {
            this.assemblyLine = assemblyLine;
            return new Builder<>(this);
        }

        public Builder<IN, OUT> accumulator(Accumulator accumulator) {
            this.accumulator = accumulator;
            return this;
        }

        public <C> Builder<IN, C> collector(Collector<OUT, ?, C> collector) {
            this.collector = collector;
            return new Builder<>(this);
        }

        public PipelineDefinition<IN, OUT> build() {
            return new PipelineDefinition<>(func, assemblyLine, accumulator, collector);
        }
    }

    public static class Accumulator {
        private final CollectionSupplier collectionSupplier;

        public Accumulator(CollectionSupplier collectionSupplier) {
            this.collectionSupplier = collectionSupplier;
        }

        public CollectionSupplier getCollectionSupplier() {
            return collectionSupplier;
        }

        public enum CollectionSupplier {
            LIST(ArrayList::new), SET(HashSet::new);

            private final Supplier<Collection<?>> supplier;

            private CollectionSupplier(Supplier<Collection<?>> supplier) {
                this.supplier = supplier;
            }

            public Supplier<Collection<?>> getSupplier() {
                return supplier;
            }
        }
    }

    public static class ListAccumulator extends Accumulator {
        public ListAccumulator() {
            super(CollectionSupplier.LIST);
        }
    }

    public static class SetAccumulator extends Accumulator {
        public SetAccumulator() {
            super(CollectionSupplier.SET);
        }
    }
}
