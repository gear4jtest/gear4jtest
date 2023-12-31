package io.github.gear4jtest.core.model;

import java.util.function.Predicate;

import io.github.gear4jtest.core.context.ItemExecution;

public class SignalModel<IN> extends BaseLineModel<IN, IN> {

	private SignalType signalType;

	private Predicate<SignalInterpretationContext<IN>> condition;

	public SignalType getSignalType() {
		return signalType;
	}

	public Predicate<SignalInterpretationContext<IN>> getCondition() {
		return condition;
	}

	public static class Builder<IN> {

		private final SignalModel<IN> managedInstance;

		Builder() {
			managedInstance = new SignalModel<>();
		}

		public Builder<IN> type(SignalType signalType) {
			managedInstance.signalType = signalType;
			return this;
		}

		public Builder<IN> condition(Predicate<SignalInterpretationContext<IN>> condition) {
			managedInstance.condition = condition;
			return this;
		}

		public SignalModel<IN> build() {
			return managedInstance;
		}

	}

	public static class SignalInterpretationContext<T> {
		private T item;
		private ItemExecution itemExecution;

		public T getItem() {
			return item;
		}

		public ItemExecution getItemExecution() {
			return itemExecution;
		}

	}

	public enum SignalType {
		STOP, FATAL;
	}

}
