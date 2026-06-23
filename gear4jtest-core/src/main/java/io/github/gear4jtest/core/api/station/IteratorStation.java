package io.github.gear4jtest.core.api.station;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

import io.github.gear4jtest.core.api.config.FlowConfig;
import io.github.gear4jtest.core.api.context.ExecutionContext;

/**
 * Station that iterates over elements produced from the input and executes a
 * child sequence for each item.
 */
public class IteratorStation<IN, OUT> extends AbstractStation<IN, OUT> {
    private final Function<IN, ? extends Iterable<?>> func;
    private final SequenceStation<?, ?> chain;
    private final ItemIdResolver itemIdResolver;
    private final FlowConfig flowConfig;
    private final Accumulator accumulator;
    private final Collector<?, ?, ?> collector;

    private IteratorStation(String id,
                            Function<IN, ? extends Iterable<?>> func,
                            SequenceStation<?, ?> chain,
                            ItemIdResolver itemIdResolver,
                            FlowConfig flowConfig,
                            Accumulator accumulator,
                            Collector<?, ?, ?> collector) {
        super(id, StationKind.ITERATOR, null, null, null, false, null, null);
        this.func = func;
        this.chain = chain;
        this.itemIdResolver = itemIdResolver;
        this.flowConfig = flowConfig;
        this.accumulator = accumulator;
        this.collector = collector;
    }

    public Function<IN, ? extends Iterable<?>> getFunc() {
        return func;
    }

    public Accumulator getAccumulator() {
        return accumulator;
    }

    public Collector<?, ?, ?> getCollector() {
        return collector;
    }

    public SequenceStation<?, ?> getChain() {
        return chain;
    }

    public ItemIdResolver getItemIdResolver() {
        return itemIdResolver;
    }

    public FlowConfig getFlowConfig() {
        return flowConfig;
    }

    @FunctionalInterface
    public interface ItemIdResolver {
        String resolve(Object element, long index, ExecutionContext ctx);
    }

    public static class Builder<IN, OUT> {
        private final String id;
        private Function<IN, ? extends Iterable<?>> func;
        private SequenceStation<?, ?> chain;
        private ItemIdResolver itemIdResolver;
        private FlowConfig flowConfig;
        private Accumulator accumulator;
        private Collector<?, ?, ?> collector;

        public Builder(String id) {
            this.id = id;
        }

        private <PREVIOUS_OUT> Builder(Builder<IN, PREVIOUS_OUT> source) {
            this.id = source.id;
            this.func = source.func;
            this.chain = source.chain;
            this.itemIdResolver = source.itemIdResolver;
            this.flowConfig = source.flowConfig;
            this.accumulator = source.accumulator;
            this.collector = source.collector;
        }

        public <A> Builder<IN, A> iterableFunction(Function<IN, ? extends Iterable<A>> func) {
            this.func = func;
            return new Builder<>(this);
        }

        public <A> Builder<IN, A> sequence(SequenceStation<OUT, A> sequenceStation) {
            this.chain = sequenceStation;
            return new Builder<>(this);
        }

        public Builder<IN, OUT> flowConfig(FlowConfig flowConfig) {
            this.flowConfig = flowConfig;
            return this;
        }

        public Builder<IN, OUT> accumulator(Accumulator accumulator) {
            this.accumulator = accumulator;
            return this;
        }

        public <C> Builder<IN, C> collector(Collector<OUT, ?, C> collector) {
            this.collector = collector;
            return new Builder<>(this);
        }

        public IteratorStation<IN, OUT> build() {
            return new IteratorStation<>(id, func, chain, itemIdResolver, flowConfig, accumulator, collector);
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

            private final Supplier<Collection<Object>> supplier;

            CollectionSupplier(Supplier<Collection<Object>> supplier) {
                this.supplier = supplier;
            }

            public Supplier<Collection<Object>> getSupplier() {
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
