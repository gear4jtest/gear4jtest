package io.github.gear4jtest.core.model;

import java.util.function.Predicate;

import io.github.gear4jtest.core.persistence.StationLog;

public class SignalDefiinition<IN> extends AbstractStation<IN, IN> {

	protected SignalType signalType;

	protected Predicate<SignalInterpretationContext<IN>> condition;

	public SignalDefiinition() {
		super("", StationKind.SIGNAL);
	}

	public SignalType getSignalType() {
		return signalType;
	}

	public Predicate<SignalInterpretationContext<IN>> getCondition() {
		return condition;
	}

	@Override
	public IN doExecute(IN input, ExecutionContext context, StationExecutionContext operationExecution) {
		switch(signalType) {
			case FATAL -> operationExecution.getRecord().setStatus(StationLog.Status.FAILED);
			case STOP -> operationExecution.getRecord().setStatus(StationLog.Status.STOPPED);
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
