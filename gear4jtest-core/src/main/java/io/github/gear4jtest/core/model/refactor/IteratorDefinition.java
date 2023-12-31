package io.github.gear4jtest.core.model.refactor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

public class IteratorDefinition<IN> extends OperationDefinition<IN, IN> {

	private Function<?, ? extends Iterable<?>> func;
	private OperationDefinition<?, ?> element;
	private Accumulator accumulator;
	private Collector collector;

	private IteratorDefinition() {
	}

	public Function<?, ? extends Iterable<?>> getFunc() {
		return func;
	}

	public OperationDefinition<?, ?> getElement() {
		return element;
	}

	public Accumulator getAccumulator() {
		return accumulator;
	}

	public Collector getCollector() {
		return collector;
	}

	public static class Builder<IN> {

		private final IteratorDefinition<IN> managedInstance;

		public Builder() {
			managedInstance = new IteratorDefinition<>();
		}

		public Builder<IN> iterableFunction(Function<?, ? extends Iterable<?>> func) {
			managedInstance.func = func;
			return this;
		}

		public Builder<IN> nestedElement(OperationDefinition<?, ?> element) {
			managedInstance.element = element;
			return this;
		}

		public Builder<IN> accumulator(Accumulator accumulator) {
			managedInstance.accumulator = accumulator;
			return this;
		}

		public Builder<IN> collector(Collector collector) {
			managedInstance.collector = collector;
			return this;
		}

		public IteratorDefinition<IN> build() {
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
