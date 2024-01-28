package io.github.gear4jtest.core.model.refactor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Collector;

import io.github.gear4jtest.core.context.LineOperatorExecution;
import io.github.gear4jtest.core.model.refactor.IteratorDefinition.ListAccumulator;
import io.github.gear4jtest.core.model.refactor.IteratorDefinition.SetAccumulator;

public class LineDefinition<X, Y> {
	private StartingPointDefinition<X> startingPoint;
	private List<OperationDefinition<?, ?>> lineOperators;
	private BiPredicate<X, LineOperatorExecution> condition;

	public LineDefinition(StartingPointDefinition<X> startingPoint) {
		this.startingPoint = startingPoint;
		this.lineOperators = new ArrayList<>();
	}

	public List<OperationDefinition<?, ?>> getLineOperators() {
		return lineOperators;
	}

	public StartingPointDefinition<X> getStartingPoint() {
		return startingPoint;
	}

	public BiPredicate<X, LineOperatorExecution> getCondition() {
		return condition;
	}

	public static class Builder<IN, OUT> {

		private final LineDefinition<IN, OUT> managedInstance;

		public Builder(StartingPointDefinition<IN> startingPoint) {
			managedInstance = new LineDefinition<>(startingPoint);
		}

		public <A> Builder<IN, A> operator(OperationDefinition<OUT, A> operator) {
			managedInstance.lineOperators.add(operator);
			return (Builder<IN, A>) this;
		}

		public UnpredictableLineDefinition.Builder<IN, OUT> operator(StopSignalDefiinition<OUT> operator) {
			managedInstance.lineOperators.add(operator);
			return new UnpredictableLineDefinition.Builder<IN, OUT>(this);
		}

		public <A, B> Builder<IN, Void> iterate(Function<OUT, ? extends Iterable<A>> iterableFunction,
				OperationDefinition<A, B> nestedElement) {
			OperationDefinition<?, ?> operator = new IteratorDefinition.Builder<IN>()
					.iterableFunction(iterableFunction)
					.nestedElement(nestedElement)
					.build();
			managedInstance.lineOperators.add(operator);
			return (Builder<IN, Void>) this;
		}

		public <A, B> Builder<IN, List<B>> iterate(Function<OUT, ? extends Iterable<A>> iterableFunction,
				OperationDefinition<A, B> nestedElement, ListAccumulator accumulator) {
			OperationDefinition<?, ?> operator = new IteratorDefinition.Builder<IN>()
					.iterableFunction(iterableFunction)
					.nestedElement(nestedElement)
					.accumulator(accumulator)
					.build();
			managedInstance.lineOperators.add(operator);
			return (Builder<IN, List<B>>) this;
		}
		
		// THINK ABOUT A toMap() COLLECTOR AND GROUP THESE 2 IN A Collection<?> COLLECTOR
		public <A, B> Builder<IN, Set<B>> iterate(Function<OUT, ? extends Iterable<A>> iterableFunction,
				OperationDefinition<A, B> nestedElement, SetAccumulator accumulator) {
			OperationDefinition<?, ?> operator = new IteratorDefinition.Builder<IN>()
					.iterableFunction(iterableFunction)
					.nestedElement(nestedElement)
					.accumulator(accumulator)
					.build();
			managedInstance.lineOperators.add(operator);
			return (Builder<IN, Set<B>>) this;
		}

		public <A, B, C> Builder<IN, C> iterate(Function<OUT, ? extends Iterable<A>> iterableFunction,
				OperationDefinition<A, B> nestedElement, Collector<B, ?, C> collector) {
			OperationDefinition<?, ?> operator = new IteratorDefinition.Builder<IN>()
					.iterableFunction(iterableFunction)
					.nestedElement(nestedElement)
					.collector(collector)
					.build();
			managedInstance.lineOperators.add(operator);
			return (Builder<IN, C>) this;
		}

		public Builder<IN, OUT> condition(BiPredicate<IN, LineOperatorExecution> condition) {
			managedInstance.condition = condition;
			return this;
		}

		public LineDefinition<IN, OUT> build() {
			return managedInstance;
		}

	}

}
