package io.github.gear4jtest.core.model.refactor;

import java.util.function.Predicate;

public class SignalDefiinition<IN> extends AbstractOperationDefinition<IN, IN> {

	protected SignalType signalType;

	protected Predicate<SignalInterpretationContext<IN>> condition;

	public SignalDefiinition() {
		super("");
	}

	public SignalType getSignalType() {
		return signalType;
	}

	public Predicate<SignalInterpretationContext<IN>> getCondition() {
		return condition;
	}

	@Override
	public IN execute(IN input, ExecutionContext context, OperationExecution operationExecution) throws Exception {
		switch(signalType) {
			case FATAL -> operationExecution.getReport().setStatus(OperationExecution.OperationReport.Status.FAILED);
			case STOP -> operationExecution.getReport().setStatus(OperationExecution.OperationReport.Status.STOPPED);
		}
		return input;
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
		private ExecutionContext itemExecution;

		public SignalInterpretationContext(T item, ExecutionContext itemExecution) {
			this.item = item;
			this.itemExecution = itemExecution;
		}

		public T getItem() {
			return item;
		}

		public ExecutionContext getItemExecution() {
			return itemExecution;
		}

	}
}
