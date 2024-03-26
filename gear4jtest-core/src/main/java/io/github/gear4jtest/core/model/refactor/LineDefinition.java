package io.github.gear4jtest.core.model.refactor;

import io.github.gear4jtest.core.context.LineOperatorExecution;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

public class LineDefinition<X, Y> extends OperationDefinition<X, Y> {
	private List<OperationDefinition<?, ?>> operators;
	private BiPredicate<X, LineOperatorExecution> condition;

	public LineDefinition() {
		this.operators = new ArrayList<>();
	}

	public List<OperationDefinition<?, ?>> getOperators() {
		return operators;
	}

	public BiPredicate<X, LineOperatorExecution> getCondition() {
		return condition;
	}

	public static class Builder<IN, OUT> {

		private final LineDefinition<IN, OUT> managedInstance;

		public Builder() {
			managedInstance = new LineDefinition<>();
		}

		public <A> Builder<IN, A> operator(OperationDefinition<OUT, A> operator) {
			managedInstance.operators.add(operator);
			return (Builder<IN, A>) this;
		}

		public UnpredictableLineDefinition.Builder<IN, OUT> operator(StopSignalDefiinition<OUT> operator) {
			managedInstance.operators.add(operator);
			return new UnpredictableLineDefinition.Builder<>(this);
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
