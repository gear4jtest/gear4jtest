package io.github.gear4jtest.core.model.refactor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

import io.github.gear4jtest.core.model.ElementModelBuilders;

public class IteratorDefinition<IN, OUT> extends OperationDefinition<IN, OUT> {

	private Function<?, ? extends Iterable<?>> func;
	private LineDefinition<?, ?> element;
	private Accumulator accumulator;
	private Collector collector;

	private IteratorDefinition() {
	}

	public Function<?, ? extends Iterable<?>> getFunc() {
		return func;
	}

	public LineDefinition<?, ?> getElement() {
		return element;
	}

	public Accumulator getAccumulator() {
		return accumulator;
	}

	public Collector getCollector() {
		return collector;
	}

	public static class Builder<IN, OUT> {

		private final IteratorDefinition<IN, OUT> managedInstance;

		public Builder() {
			managedInstance = new IteratorDefinition<>();
		}

		public <A> Builder<IN, A> iterableFunction(Function<IN, ? extends Iterable<A>> func) {
			managedInstance.func = func;
			return (Builder<IN, A>) this;
		}

		public <A> Builder<IN, A> nestedElement(OperationDefinition<OUT, A> element) {
			managedInstance.element = ElementModelBuilders.line(element).build();
			return (Builder<IN, A>) this;
		}

		public Builder<IN, OUT> accumulator(Accumulator accumulator) {
			managedInstance.accumulator = accumulator;
			return this;
		}

		public <C> Builder<IN, C> collector(Collector<OUT, ?, C> collector) {
			managedInstance.collector = collector;
			return (Builder<IN, C>) this;
		}

		public IteratorDefinition<IN, OUT> build() {
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
