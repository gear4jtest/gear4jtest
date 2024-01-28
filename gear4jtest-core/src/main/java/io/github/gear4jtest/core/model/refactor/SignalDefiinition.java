package io.github.gear4jtest.core.model.refactor;

import java.util.function.Predicate;

import io.github.gear4jtest.core.context.AssemblyLineOperatorExecution;

public class SignalDefiinition<IN> extends IdentityOperationDefinition<IN> {

	protected SignalType signalType;

	protected Predicate<SignalInterpretationContext<IN>> condition;

	public SignalType getSignalType() {
		return signalType;
	}

	public Predicate<SignalInterpretationContext<IN>> getCondition() {
		return condition;
	}

	public static class Builder<IN> {

		private final SignalDefiinition<IN> managedInstance;

		public Builder() {
			managedInstance = new SignalDefiinition<>();
		}

		public Builder<IN> type(SignalType signalType) {
			managedInstance.signalType = signalType;
			return this;
		}

		public Builder<IN> condition(Predicate<SignalInterpretationContext<IN>> condition) {
			managedInstance.condition = condition;
			return this;
		}

		public SignalDefiinition<IN> build() {
			return managedInstance;
		}

	}

	public static class SignalInterpretationContext<T> {
		private T item;
		private AssemblyLineOperatorExecution itemExecution;

		public SignalInterpretationContext(T item, AssemblyLineOperatorExecution itemExecution) {
			this.item = item;
			this.itemExecution = itemExecution;
		}

		public T getItem() {
			return item;
		}

		public AssemblyLineOperatorExecution getItemExecution() {
			return itemExecution;
		}

	}

	public enum SignalType {
		STOP, FATAL;
	}

}
