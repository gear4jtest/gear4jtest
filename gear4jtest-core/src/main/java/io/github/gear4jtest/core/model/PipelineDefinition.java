package io.github.gear4jtest.core.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

public class PipelineDefinition<IN, OUT> extends AbstractStation<IN, OUT> {

	private Function<IN, ? extends Iterable<?>> func;
	private AssemblyLine assemblyLine;
	private Accumulator accumulator;
	private Collector collector;

	private PipelineDefinition() {
		super("", StationKind.OTHER);
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

//	@Override
//	public OUT doExecute(IN input, ExecutionContext context, StationExecutionContext operationExecution) throws Exception {
//		Iterable<?> collection = null;
//		if (func != null) {
//			collection = func.apply(input);
//		} else {
//			collection = (Iterable<?>) input;
//		}
//		Collection<Object> results = new ArrayList<>();
//		for (Object element: collection) {
//			var result = assemblyLine.execute(element, context.getContext(), context.getResourceFactory(), context.getAssemblyRunManager());
//
//			if (!result.isSuccess()) {
//				return null;
//			}
//
//			results.add(result.getResult());
//		}

//		if (accumulator != null) {
//			Collection<?> acc = accumulator.getCollectionSupplier().getSupplier().get();
//			results.forEach(r -> {
//				if (r.isSuccess()) {
//					acc.add(r.getResult());
//				} else {
//					report.addError(r.getError());
//				}
//			});
//		if (collector != null) {
//			return (OUT) results.stream().collect(collector);
//		} else {
//			return (OUT) results;
//		}
//		}
//		return new ExecutionResult<>((OUT) results, true, null, report);
//	}

	public static class Builder<IN, OUT> {

		private final PipelineDefinition<IN, OUT> managedInstance;

		public Builder() {
			managedInstance = new PipelineDefinition<>();
		}

		public <A> Builder<IN, A> iterableFunction(Function<IN, ? extends Iterable<A>> func) {
			managedInstance.func = func;
			return (Builder<IN, A>) this;
		}

		public <A> Builder<IN, A> pipeline(AssemblyLine<OUT, A> assemblyLine) {
			managedInstance.assemblyLine = assemblyLine;
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

		public PipelineDefinition<IN, OUT> build() {
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
