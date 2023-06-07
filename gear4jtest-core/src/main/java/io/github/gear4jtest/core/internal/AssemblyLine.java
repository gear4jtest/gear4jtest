package io.github.gear4jtest.core.internal;

import java.util.Map;
import java.util.UUID;

import io.github.gear4jtest.core.context.AssemblyLineExecution;

public class AssemblyLine<INPUT, OUTPUT> {

	private final UUID id;
	private final LineElement startingElement;
	private final Map<String, Object> context;

	public AssemblyLine(Map<String, Object> context) {
		this(null, context);
	}

	public AssemblyLine(LineElement startingElement, Map<String, Object> context) {
		this.id = UUID.randomUUID();
		this.startingElement = startingElement;
		this.context = context;
	}
	
	public OUTPUT execute(INPUT input, AssemblyLineExecution execution) {
		Item item = new AssemblyLineOrchestrator(execution).command(this, new Item(input, execution.createItemExecution()));
		return (OUTPUT) item.getItem();
	}

	public UUID getId() {
		return id;
	}

	public LineElement getStartingElement() {
		return startingElement;
	}

	public Map<String, Object> getContext() {
		return context;
	}

}
