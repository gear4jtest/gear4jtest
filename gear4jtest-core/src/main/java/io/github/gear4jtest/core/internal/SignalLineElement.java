package io.github.gear4jtest.core.internal;

import io.github.gear4jtest.core.context.ItemExecution;
import io.github.gear4jtest.core.context.LineElementExecution;
import io.github.gear4jtest.core.context.SignalExecution;
import io.github.gear4jtest.core.model.refactor.SignalDefiinition;
import io.github.gear4jtest.core.model.refactor.SignalDefiinition.SignalInterpretationContext;

public class SignalLineElement extends LineElement {

	private final SignalDefiinition<?> signal;

	public SignalLineElement(SignalDefiinition<?> signal) {
		super();
		this.signal = signal;
	}

	@Override
	public LineElementExecution execute(ItemExecution itemExecution) {
		SignalExecution signalExecution = itemExecution.createSignalExecution(this);

		boolean shouldSignalBeFired = signal.getCondition()
				.test(new SignalInterpretationContext(itemExecution.getItem().getItem(), itemExecution));

		if (shouldSignalBeFired) {
			switch (signal.getSignalType()) {
			case STOP:
				itemExecution.shouldStop(true);
				break;
			case FATAL:
				itemExecution.shouldStop(true);
				signalExecution.registerThrowable(new RuntimeException());
				break;
			}
		}
		return signalExecution;
	}

}
