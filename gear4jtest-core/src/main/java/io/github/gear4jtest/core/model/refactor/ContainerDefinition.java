package io.github.gear4jtest.core.model.refactor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

public class ContainerDefinition<IN, OUT> extends OperationDefinition<IN, OUT> {

	private List<LineDefinition<?, ?>> subLines;
	private Function<?, ?> func;
	private BiFunction<?, ?, ?> biFunc;

	private ContainerDefinition() {
		this.subLines = new ArrayList<>();
	}

	public List<LineDefinition<?, ?>> getChildren() {
		return subLines;
	}
	
	public Function<?, ?> getFunc() {
		return func;
	}

	public BiFunction<?, ?, ?> getBiFunc() {
		return biFunc;
	}

	public static class Builder<IN, OUT> {

		private final ContainerDefinition<IN, OUT> managedInstance;

		public Builder() {
			managedInstance = new ContainerDefinition<>();
		}

		public <START, B> Builder<START, OUT> withSubLine(LineDefinition<START, B> startingElement) {
			managedInstance.subLines.add(startingElement);
			return (Builder<START, OUT>) this;
		}

		public <START, B, C> Builder<START, C> withSubLineAndReturns(LineDefinition<START, B> startingElement, Function<B, C> func) {
			managedInstance.subLines.add(startingElement);
			managedInstance.func = func;
			return (Builder<START, C>) this;
		}

		public <START, B> Builder<START, OUT> withTwoSubLines(LineDefinition<START, B> startingElement, LineDefinition<IN, B> startingElement2) {
			managedInstance.subLines.add(startingElement);
			managedInstance.subLines.add(startingElement2);
			return (Builder<START, OUT>) this;
		}

		public <START, B, C, D> Builder<START, C> withTwoSubLinesAndReturns(LineDefinition<START, B> startingElement, LineDefinition<START, C> startingElement2, BiFunction<B, C, D> func) {
			managedInstance.subLines.add(startingElement);
			managedInstance.subLines.add(startingElement2);
			managedInstance.biFunc = func;
			return (Builder<START, C>) this;
		}

		public ContainerDefinition<IN, OUT> build() {
			return managedInstance;
		}

	}

}
