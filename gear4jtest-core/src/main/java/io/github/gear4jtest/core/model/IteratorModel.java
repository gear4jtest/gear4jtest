package io.github.gear4jtest.core.model;

import java.util.function.Function;

public class IteratorModel<IN> extends BaseLineModel<IN, IN> {

	private Function<?, ? extends Iterable<?>> func;
	private BaseLineModel<?, ?> element;

	private IteratorModel() {
	}

	public Function<?, ? extends Iterable<?>> getFunc() {
		return func;
	}

	public BaseLineModel<?, ?> getElement() {
		return element;
	}

	public static class Builder<IN> {

		private final IteratorModel<IN> managedInstance;

		Builder() {
			managedInstance = new IteratorModel<>();
		}

		public Builder<IN> iterableFunction(Function<?, ? extends Iterable<?>> func) {
			managedInstance.func = func;
			return this;
		}

		public Builder<IN> nestedElement(BaseLineModel<?, ?> element) {
			managedInstance.element = element;
			return this;
		}

		public IteratorModel<IN> build() {
			return managedInstance;
		}

	}

}
