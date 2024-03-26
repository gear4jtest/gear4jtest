package io.github.gear4jtest.core.model.refactor;

import java.util.List;

public class ContainerBaseDefinition<IN, OUT> extends OperationDefinition<IN, OUT> {

	protected final List<LineDefinition<?, ?>> subLines;
	protected ContainerFunction func;

	public ContainerBaseDefinition(List<LineDefinition<?, ?>> subLines, ContainerFunction func) {
		this.subLines = subLines;
		this.func = func;
	}

	public List<LineDefinition<?, ?>> getSubLines() {
		return subLines;
	}

	public ContainerFunction getFunc() {
		return func;
	}

	public static class Builder<IN, OUT> {
		public Builder() {
		}

		public <A> Container1Definition.Builder<IN, OUT, A> withSubLine(LineDefinition<OUT, A> startingElement) {
			return new Container1Definition.Builder<>(startingElement);
		}
	}

	@FunctionalInterface
	public interface ContainerFunction {
		Object apply(Object... objects);
	}
}
