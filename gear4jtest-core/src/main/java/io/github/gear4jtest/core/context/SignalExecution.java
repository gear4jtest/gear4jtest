package io.github.gear4jtest.core.context;

import java.util.Map;
import java.util.UUID;

import io.github.gear4jtest.core.internal.SignalLineElement;

public class SignalExecution extends LineElementExecution {

	private final UUID id;

	public SignalExecution(ItemExecution itemExecution, SignalLineElement element) {
		super(itemExecution);
		this.id = element.getId();
	}

	public UUID getId() {
		return id;
	}

	public Map<String, Object> getChainContext() {
		return getItemExecution().getAssemblyLineExecution().getContext();
	}

}
