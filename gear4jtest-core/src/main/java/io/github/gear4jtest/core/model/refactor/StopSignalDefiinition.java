package io.github.gear4jtest.core.model.refactor;

import java.util.function.Predicate;

public class StopSignalDefiinition<IN> extends SignalDefiinition<IN> {

	public static class Builder<IN> {

		private final StopSignalDefiinition<IN> managedInstance;

		public Builder() {
			managedInstance = new StopSignalDefiinition<>();
		}

		public Builder<IN> type(SignalType signalType) {
			managedInstance.signalType = signalType;
			return this;
		}

		public Builder<IN> condition(Predicate<SignalInterpretationContext<IN>> condition) {
			managedInstance.condition = condition;
			return this;
		}

		public StopSignalDefiinition<IN> build() {
			return managedInstance;
		}

	}

}
