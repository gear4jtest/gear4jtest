package io.github.gear4jtest.core.internal;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.github.gear4jtest.core.context.AssemblyLineExecution;
import io.github.gear4jtest.core.event.EventQueue;
import io.github.gear4jtest.core.event.EventQueueService;
import io.github.gear4jtest.core.event.EventTriggerService;
import io.github.gear4jtest.core.factory.ResourceFactory;
import io.github.gear4jtest.core.model.ChainModel;
import io.github.gear4jtest.core.model.Queue;

/**
 * Entry class for Gear4j chains execution.
 * 
 * @author quleb
 *
 */
public class ChainExecutorService {

	public <BEGIN, IN> IN execute(ChainModel<BEGIN, IN> chain, BEGIN object) {
		return execute(chain, object, null);
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
		AssemblyLine<BEGIN, IN> line = new AssemblyLineBuilder<>(chain, context).buildAssemblyLine();
		AssemblyLineExecution execution = new AssemblyLineExecution(context);
		
		initializeQueues(chain.getEventHandling().getQueues(), line.getId(), chain.getResourceFactory());
		return line.execute(object, execution);
	}
	
	private static void initializeQueues(List<Queue> queues, UUID lineId, ResourceFactory resourceFactory) {
		List<EventQueue> eventQueues = new EventQueueService(queues).initializeQueues();
		EventTriggerService service = new EventTriggerService(eventQueues, resourceFactory);
		ServiceRegistry.pushEventTriggerService(lineId, service);
	}

}
