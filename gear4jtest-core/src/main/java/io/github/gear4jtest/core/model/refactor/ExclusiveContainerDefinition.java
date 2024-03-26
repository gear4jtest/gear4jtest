package io.github.gear4jtest.core.model.refactor;

import java.util.ArrayList;
import java.util.List;

public class ExclusiveContainerDefinition<IN, OUT> extends OperationDefinition<IN, OUT> {

	private List<LineDefinition<?, ?>> subLines;

	private ExclusiveContainerDefinition(LineDefinition<IN, OUT> firstLine) {
		this.subLines = new ArrayList<>();
	}

	public List<LineDefinition<?, ?>> getChildren() {
		return subLines;
	}

	public static class Builder<IN, OUT> {

		private final ExclusiveContainerDefinition<IN, OUT> managedInstance;

		public Builder(LineDefinition<IN, OUT> firstLine) {
			managedInstance = new ExclusiveContainerDefinition<>(firstLine);
			withIfLine(firstLine);
		}

		public Builder<IN, OUT> withIfLine(LineDefinition<IN, OUT> startingElement) {
			managedInstance.subLines.add(startingElement);
			return this;
		}

		// Optional : automatic identity line added on else behaviour
		// fatalOnNoExecution ?
		public ExclusiveContainerDefinition<IN, OUT> withElseLine(LineDefinition<IN, OUT> startingElement) {
			managedInstance.subLines.add(startingElement);
			return this.managedInstance;
		}

	}

}
