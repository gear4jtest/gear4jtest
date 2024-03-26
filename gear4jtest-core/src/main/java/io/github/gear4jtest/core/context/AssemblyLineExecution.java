package io.github.gear4jtest.core.context;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.github.gear4jtest.core.event.EventTriggerService;
import io.github.gear4jtest.core.event.NoOpEventTriggerService;
import io.github.gear4jtest.core.event.SimpleEventTriggerService;
import io.github.gear4jtest.core.internal.AssemblyLineOperator;
import io.github.gear4jtest.core.internal.ContainerLineElement;
import io.github.gear4jtest.core.internal.Item;
import io.github.gear4jtest.core.internal.IteratorLineElement;
import io.github.gear4jtest.core.internal.LineOperator;
import io.github.gear4jtest.core.internal.SignalLineElement;
import io.github.gear4jtest.core.internal.StepLineElement;

public class AssemblyLineExecution {

	private UUID id;
	
	//TODO(all): make this execution be linked with an instance of the assembly line (useful for batch restarting)...
//	private AssemblyLine line;

	private LocalDateTime startTime = null;

	private LocalDateTime createTime = LocalDateTime.now();

	private LocalDateTime endTime = null;

	private LocalDateTime lastUpdateTime = null;

	private Item item;

	private LineOperatorExecution mainLineExecution;

	private Map<String, Object> context;
	
	private EventTriggerService eventTriggerService;
	
	private List<Throwable> throwables = new ArrayList<>();

	public AssemblyLineExecution(Map<String, Object> context, Object input) {
		this.id = UUID.randomUUID();
		this.item = new Item(input);
		this.context = context;
		this.eventTriggerService = new NoOpEventTriggerService();
	}

//	public ItemExecution createItemExecution(Object input) {
//		return createItemExecution(input, new HashMap<>());
//	}
//
//	public ItemExecution createItemExecution(Object input, Map<String, Object> context) {
//		LineOperatorExecution itemExecution = new LineOperatorExecution(this, input, context);
//		this.mainLineExecution = itemExecution;
//		return itemExecution;
//	}

	public LineOperatorExecution createLineExecution(LineOperator element, Object input) {
		LineOperatorExecution itemExecution = new LineOperatorExecution(element, null, this);
		this.mainLineExecution = itemExecution;
		return itemExecution;
	}

	public <A extends AssemblyLineOperatorExecution> A createExecution(AssemblyLineOperator<A> element, ExecutionContainer<?> parentLineOperatorExecution) {
		A lineElementExecution = buildExecution(element, parentLineOperatorExecution);
		parentLineOperatorExecution.getExecutions().add(lineElementExecution);
		return lineElementExecution;
	}

	private <A extends AssemblyLineOperatorExecution> A buildExecution(AssemblyLineOperator<A> element, ExecutionContainer<?> parentOperatorExecution) {
		if (element instanceof StepLineElement) {
			return (A) new StepExecution((StepLineElement) element, parentOperatorExecution, this);
		} else if (element instanceof SignalLineElement) {
			return (A) new SignalExecution((SignalLineElement) element, parentOperatorExecution, this);
		} else if (element instanceof IteratorLineElement) {
			return (A) new IteratorExecution((IteratorLineElement) element, parentOperatorExecution, this);
		} else if (element instanceof ContainerLineElement) {
			return (A) new ContainerExecution((ContainerLineElement) element, parentOperatorExecution, this);
		} else if (element instanceof LineOperator) {
			return (A) new LineOperatorExecution((LineOperator) element, parentOperatorExecution, this);
		} else {
			throw new IllegalArgumentException();
		}
	}

	public UUID getId() {
		return id;
	}

	public LocalDateTime getStartTime() {
		return startTime;
	}

	public void setStartTime(LocalDateTime startTime) {
		this.startTime = startTime;
	}

	public LocalDateTime getCreateTime() {
		return createTime;
	}

	public void setCreateTime(LocalDateTime createTime) {
		this.createTime = createTime;
	}

	public LocalDateTime getEndTime() {
		return endTime;
	}

	public void setEndTime(LocalDateTime endTime) {
		this.endTime = endTime;
	}

	public LocalDateTime getLastUpdateTime() {
		return lastUpdateTime;
	}

	public void setLastUpdateTime(LocalDateTime lastUpdateTime) {
		this.lastUpdateTime = lastUpdateTime;
	}

	public Map<String, Object> getContext() {
		return context;
	}

	public void setContext(Map<String, Object> context) {
		this.context = context;
	}

	public LineOperatorExecution getMainLineExecution() {
		return mainLineExecution;
	}

	public void registerEventTriggerService(SimpleEventTriggerService service) {
		this.eventTriggerService = service;
	}

	public EventTriggerService getEventTriggerService() {
//		return ServiceRegistry.getEventPublisher(id);
		return eventTriggerService;
	}

	public List<Throwable> getThrowables() {
		return throwables;
	}

	public void registerThrowable(Throwable throwable) {
		this.throwables.add(throwable);
	}

	public Item getItem() {
		return item;
	}

}
