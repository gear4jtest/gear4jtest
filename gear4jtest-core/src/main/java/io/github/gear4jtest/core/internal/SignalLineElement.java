package io.github.gear4jtest.core.internal;

import io.github.gear4jtest.core.context.LineElementExecution;
import io.github.gear4jtest.core.context.SignalExecution;
import io.github.gear4jtest.core.model.refactor.SignalDefiinition;
import io.github.gear4jtest.core.model.refactor.SignalDefiinition.SignalInterpretationContext;

public class SignalLineElement extends LineElement<SignalExecution> {

	private final SignalDefiinition<?> signal;

	public SignalLineElement(SignalDefiinition<?> signal) {
		super();
		this.signal = signal;
	}

	@Override
	public LineElementExecution execute(SignalExecution execution) {
		boolean shouldSignalBeFired = signal.getCondition()
				.test(new SignalInterpretationContext(execution.getItemExecution().getItem().getItem(), execution.getItemExecution()));

		if (shouldSignalBeFired) {
			switch (signal.getSignalType()) {
			case STOP:
				execution.getItemExecution().shouldStop(true);
				break;
			case FATAL:
				execution.getItemExecution().shouldStop(true);
				execution.registerThrowable(new RuntimeException());
				break;
			}
		}
		return execution;
	}

}
