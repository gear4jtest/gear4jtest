package io.github.gear4jtest.core.internal;

import java.util.Map;

import io.github.gear4jtest.core.context.AssemblyLineExecution;
import io.github.gear4jtest.core.model.ChainModel;

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
	 * @param input
	 * @return
	 */
	// Ici retourner un bean particulier
	// ChainExecutionResult<IN> qui contiendrait le retour, de type IN, s'il y a eu erreur etc...
	public <BEGIN, IN> IN execute(ChainModel<BEGIN, IN> chain, BEGIN input, Map<String, Object> context) {
		// 1 validation
		// 2 line building
		// 3 execution retrieving / building
		// 4 initializing ? initializer + trigger event line initialization
		// 5 executing ?
		
		// Validation
		// ...

		// Execution building
		//TODO(all): externalize execution creation when batch management
		AssemblyLineExecution execution = new AssemblyLineExecution(context);
		
		// Initialization
		// TODO(all): initialize method in AssemblyLine class ?
		new AssemblyLineInitializer(chain, execution).initialize();
		
		// Assembly line building
		AssemblyLine<BEGIN, IN> line = new AssemblyLineBuilder<>(chain).buildAssemblyLine();
		
//		initializeQueues(chain.getEventHandling().getQueues(), line.getId(), chain.getResourceFactory());
		return line.execute(input, execution);
	}
	
//	private static void initializeQueues(List<Queue> queues, UUID lineId, ResourceFactory resourceFactory) {
//		List<EventQueue> eventQueues = new EventQueueService(queues).initializeQueues();
//		EventTriggerService service = new EventTriggerService(eventQueues, resourceFactory);
//		ServiceRegistry.pushEventTriggerService(lineId, service);
//	}

}
