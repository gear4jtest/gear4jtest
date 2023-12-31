package io.github.gear4jtest.core.internal;

import java.util.UUID;

import io.github.gear4jtest.core.context.AssemblyLineExecution;
import io.github.gear4jtest.core.event.builders.AssemblyLineCreationEventBuilder;
import io.github.gear4jtest.core.event.builders.AssemblyLineCreationEventBuilder.AssemblyLineData;

public class AssemblyLine<INPUT, OUTPUT> {

	private final UUID id;
	private final LineElement startingElement;

	public AssemblyLine(LineElement startingElement) {
		this.id = UUID.randomUUID();
		this.startingElement = startingElement;
	}

	// SHOULD RETURN ASSEMBLYLINEEXECUTION
//	OUTPUT execute(INPUT input, AssemblyLineExecution execution) {
//		execution.getEventTriggerService().publishEvent(new AssemblyLineCreationEventBuilder().buildEvent(id, new AssemblyLineData(this)));
//		ItemExecution item = new AssemblyLineOrchestrator(execution).orchestrate(this, input);
//		return (OUTPUT) item.getItem().getItem();
//	}

	AssemblyLineExecution execute(INPUT input, AssemblyLineExecution execution) {
		execution.getEventTriggerService().publishEvent(new AssemblyLineCreationEventBuilder().buildEvent(id, new AssemblyLineData(this)));
		return new AssemblyLineOrchestrator(execution).orchestrate(this, input);
	}

	public UUID getId() {
		return id;
	}

	public LineElement getStartingElement() {
		return startingElement;
	}

}
