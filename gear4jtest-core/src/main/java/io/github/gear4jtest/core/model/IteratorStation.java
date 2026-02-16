package io.github.gear4jtest.core.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

import io.github.gear4jtest.core.engine.flow.FlowConfig;

/**
 * Itérateur d'opérations :
 * - conserve le builder et les accumulateurs d'origine
 * - n'utilise plus ExecutionReport / OperationResult
 * - branche sur PipelineExecutionManager + IteratorBatch / OperationExecutionRecord
 */
@SuppressWarnings("unchecked")
public class IteratorStation<IN, OUT> extends AbstractStation<IN, OUT> {

    private Function<IN, ? extends Iterable<?>> func;
    private SequenceStation chain;
    private ItemIdResolver itemIdResolver;
    private FlowConfig flowConfig;
    private Accumulator accumulator;
    private Collector collector;

    public IteratorStation(String id) {
        super(id, StationKind.ITERATOR);
    }

    public Function<IN, ? extends Iterable<?>> getFunc() {
        return func;
    }

    public Accumulator getAccumulator() {
        return accumulator;
    }

    public Collector getCollector() {
        return collector;
    }

    public SequenceStation getChain() {
        return chain;
    }

    public ItemIdResolver getItemIdResolver() {
        return itemIdResolver;
    }


    public FlowConfig getFlowConfig() {
        return flowConfig;
    }

    public void setFlowConfig(FlowConfig flowConfig) {
        this.flowConfig = flowConfig;
    }

    public static class Builder<IN, OUT> {

        private final IteratorStation<IN, OUT> managedInstance;

        public Builder(String id) {
            managedInstance = new IteratorStation<>(id);
        }

        public <A> Builder<IN, A> iterableFunction(Function<IN, ? extends Iterable<A>> func) {
            managedInstance.func = func;
            return (Builder<IN, A>) this;
        }

        public <A> Builder<IN, A> pipeline(SequenceStation<OUT, A> sequenceStation) {
            managedInstance.chain = sequenceStation;
            return (Builder<IN, A>) this;
        }

//		public <A> Builder<IN, A> operation(AbstractOperationDefinition<OUT, A> operation) {
//			managedInstance.operation = operation;
//			return (Builder<IN, A>) this;
//		}

        public Builder<IN, OUT> accumulator(Accumulator accumulator) {
            managedInstance.accumulator = accumulator;
            return this;
        }

        public <C> Builder<IN, C> collector(Collector<OUT, ?, C> collector) {
            managedInstance.collector = collector;
            return (Builder<IN, C>) this;
        }

        public IteratorStation<IN, OUT> build() {
            return managedInstance;
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
            LIST(ArrayList::new),
            SET(HashSet::new);

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

    @FunctionalInterface
    public interface ItemIdResolver {
        String resolve(Object element, long index, ExecutionContext ctx);
    }
}
