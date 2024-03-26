package io.github.gear4jtest.core.context;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import io.github.gear4jtest.core.event.EventTriggerService;
import io.github.gear4jtest.core.internal.AssemblyLineOperator;
import io.github.gear4jtest.core.internal.Item;

public class AssemblyLineOperatorExecution {

	private final UUID id;

	protected final Item item;
	
	private final Map<String, Object> context;

	private final AssemblyLineOperator lineElement;

	private final ExecutionContainer<?> parentOperatorExecution;

	private final AssemblyLineExecution assemblyLineExecution;

	private boolean processed;

	private boolean shouldStop;

	private Throwable throwable;

//	private SourceExhauster sourceExhauster;

	public AssemblyLineOperatorExecution(AssemblyLineOperator lineElement, ExecutionContainer<?> parentOperatorExecution, AssemblyLineExecution assemblyLineExecution) {
		this.id = UUID.randomUUID();
		this.lineElement = lineElement;
		this.parentOperatorExecution = parentOperatorExecution;
		// clone item
		this.item = new Item(Optional.ofNullable(parentOperatorExecution)
				.map(AssemblyLineOperatorExecution::getItem)
				.map(Item::getItem)
				.orElse(Optional.ofNullable(assemblyLineExecution.getItem()).map(Item::getItem).orElse(new Item(null))));
		this.context = new HashMap<>();
		this.processed = false;
		this.assemblyLineExecution = assemblyLineExecution;
	}

	public UUID getId() {
		return id;
	}

	public Item getItem() {
		return item;
	}

	public Map<String, Object> getContext() {
		return context;
	}

	public Throwable getThrowable() {
		return throwable;
	}

	public boolean shouldStop() {
		return shouldStop;
	}

	public void shouldStop(boolean shouldStop) {
		this.shouldStop = shouldStop;
	}

	public Map<String, Object> getChainContext() {
		return assemblyLineExecution.getContext();
	}

	public EventTriggerService getEventTriggerService() {
		return assemblyLineExecution.getEventTriggerService();
	}

	public AssemblyLineExecution getAssemblyLineExecution() {
		return assemblyLineExecution;
	}

	public void registerThrowable(Throwable throwable) {
		this.throwable = throwable;
		assemblyLineExecution.registerThrowable(throwable);
	}

	public ExecutionContainer<?> getParentOperatorExecution() {
		return parentOperatorExecution;
	}

}
