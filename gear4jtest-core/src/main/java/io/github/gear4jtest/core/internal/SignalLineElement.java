package io.github.gear4jtest.core.internal;

import io.github.gear4jtest.core.context.SignalExecution;
import io.github.gear4jtest.core.model.refactor.SignalDefiinition;
import io.github.gear4jtest.core.model.refactor.SignalDefiinition.SignalInterpretationContext;

public class SignalLineElement extends AssemblyLineOperator<SignalExecution> {

	private final SignalDefiinition<?> signal;

	public SignalLineElement(SignalDefiinition<?> signal) {
		super();
		this.signal = signal;
	}

	@Override
	public SignalExecution execute(SignalExecution execution) {
		boolean shouldSignalBeFired = signal.getCondition()
				.test(new SignalInterpretationContext(execution.getItem().getItem(), execution));

		if (shouldSignalBeFired) {
			switch (signal.getSignalType()) {
			case STOP:
				execution.shouldStop(true);
				break;
			case FATAL:
				execution.shouldStop(true);
				execution.registerThrowable(new RuntimeException());
				break;
			}
		}
		return execution;
	}

}
