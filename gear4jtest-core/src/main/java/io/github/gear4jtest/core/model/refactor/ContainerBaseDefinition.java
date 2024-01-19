package io.github.gear4jtest.core.model.refactor;

import java.util.function.BiPredicate;

import io.github.gear4jtest.core.context.ItemExecution;

public class ContainerBaseDefinition<IN, OUT> extends OperationDefinition<IN, OUT> {

	private ContainerBaseDefinition() {
	}

	public static class Builder<IN, OUT> {

		public Builder() {
		}

		public <A> Container1Definition.Builder<IN, OUT, A> withSubLine(LineDefinition<OUT, A> startingElement) {
			return new Container1Definition.Builder<IN, OUT, A>(startingElement);
		}

		public <A> Container1Definition.Builder<IN, OUT, A> withSubLine(LineDefinition<OUT, A> startingElement, BiPredicate<IN, ItemExecution> condition) {
			return new Container1Definition.Builder<IN, OUT, A>(startingElement, condition);
		}

	}

}
