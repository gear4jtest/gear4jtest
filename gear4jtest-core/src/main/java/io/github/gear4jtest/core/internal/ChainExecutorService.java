package io.github.gear4jtest.core.internal;

import java.util.List;
import java.util.Map;

import io.github.gear4jtest.core.context.Contexts;
import io.github.gear4jtest.core.context.Gear4jContext;
import io.github.gear4jtest.core.context.ItemContext;
import io.github.gear4jtest.core.event.EventQueue;
import io.github.gear4jtest.core.event.EventQueueService;
import io.github.gear4jtest.core.event.EventTriggerService;
import io.github.gear4jtest.core.model.ChainModel;
import io.github.gear4jtest.core.model.Queue;

/**
 * Entry class for Gear4j chains execution.
 * 
 * @author quleb
 *
 */
public class ChainExecutorService {
	
	private final LineCommander commander;
	
	public ChainExecutorService() {
		this.commander = new LineCommander();
	}
	
	/**
	 * Executes a single Gear4j chain, with the given input object.
	 * 
	 * @param <BEGIN>
	 * @param <IN>
	 * @param chain
	 * @param object
	 * @return
	 */
	// Ici retourner un bean particulier
	// ChainExecutionResult<IN> qui contiendrait le retour, de type IN, s'il y a eu erreur etc...
	public <BEGIN, IN> IN execute(ChainModel<BEGIN, IN> chain, BEGIN object, Map<String, Object> context) {
		AssemblyLine<BEGIN, IN> line = new AssemblyLineBuilder<>(chain).buildChain();
		
		EventTriggerService eventTriggerService = initializeQueues(chain.getQueues());
		Contexts<?> ctxs = initializeContexts(context, eventTriggerService);
		return (IN) commander.command(line, object, ctxs);
	}
	
	private static EventTriggerService initializeQueues(List<Queue> queues) {
		List<EventQueue> eventQueues = new EventQueueService(queues).initializeQueues();
		return new EventTriggerService(eventQueues);
	}
	
	private static Contexts<?> initializeContexts(Map<String, Object> context, EventTriggerService eventTriggerService) {
		Contexts<?> ctxs = new Contexts<>();
		ctxs.setGlobalContext(new Gear4jContext(context, eventTriggerService));
		ctxs.setItemContext(new ItemContext());
		return ctxs;
	}
	
}
