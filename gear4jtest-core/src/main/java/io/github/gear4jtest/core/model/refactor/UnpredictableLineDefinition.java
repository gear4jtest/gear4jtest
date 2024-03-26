package io.github.gear4jtest.core.model.refactor;

import java.util.function.Function;

public class UnpredictableLineDefinition<X, Y> {

	public static class Builder<IN, OUT> {

		private final LineDefinition.Builder<IN, OUT> managedInstance;

		public Builder(LineDefinition.Builder<IN, OUT> lineDefinition) {
			managedInstance = lineDefinition;
		}

		public <A> Builder<IN, A> operator(OperationDefinition<OUT, A> operator) {
			managedInstance.operator(operator);
			return (Builder<IN, A>) this;
		}

		public LineDefinition<IN, Object> build() {
			return (LineDefinition<IN, Object>) managedInstance.build();
		}

	}

}
