package io.github.gear4jtest.core.internal;

import java.util.Map;

import io.github.gear4jtest.core.context.AssemblyLineExecution;
import io.github.gear4jtest.core.factory.ResourceFactory;
import io.github.gear4jtest.core.model.refactor.AssemblyLineDefinition;

/**
 * Entry class for Gear4j chains execution.
 * 
 * @author quleb
 *
 */
public class ChainExecutorService {

	public <BEGIN, IN> IN executeAndUnwrap(AssemblyLineDefinition<BEGIN, IN> assemblyLine, BEGIN input, Map<String, Object> context, ResourceFactory resourceFactory) throws AssemblyLineException {
		AssemblyLineExecution execution = execute(assemblyLine, input, context, resourceFactory);
		
		if (!execution.getThrowables().isEmpty()) {
			throw new AssemblyLineException(execution.getThrowables());
		}
		return (IN) execution.getItemExecution().getItem().getItem();
	}
	
	public <BEGIN, IN> IN executeAndUnwrap(AssemblyLineDefinition<BEGIN, IN> assemblyLine, BEGIN input, ResourceFactory resourceFactory) throws AssemblyLineException {
		return executeAndUnwrap(assemblyLine, input, null, resourceFactory);
	}
	
	/**
	 * Executes a single Gear4j chain, with the given input object.
	 * 
	 * @param <BEGIN>
	 * @param <IN>
	 * @param assemblyLine
	 * @param input
	 * @return
	 */
	// Ici retourner un bean particulier
	// ChainExecutionResult<IN> qui contiendrait le retour, de type IN, s'il y a eu erreur etc...
	// line parameters + context ?
	public <BEGIN, IN> AssemblyLineExecution execute(AssemblyLineDefinition<BEGIN, IN> assemblyLine, BEGIN input, Map<String, Object> context, ResourceFactory resourceFactory) {
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
		new AssemblyLineInitializer(assemblyLine, execution).initialize();
		
		// Assembly line building
		AssemblyLine<BEGIN, IN> line = new AssemblyLineBuilder<>(assemblyLine, resourceFactory).buildAssemblyLine();
		
//		initializeQueues(chain.getEventHandling().getQueues(), line.getId(), chain.getResourceFactory());
		return line.execute(input, execution);
	}
	
//	private static void initializeQueues(List<Queue> queues, UUID lineId, ResourceFactory resourceFactory) {
//		List<EventQueue> eventQueues = new EventQueueService(queues).initializeQueues();
//		EventTriggerService service = new EventTriggerService(eventQueues, resourceFactory);
//		ServiceRegistry.pushEventTriggerService(lineId, service);
//	}

	public <BEGIN, IN> AssemblyLineExecution execute(AssemblyLineDefinition<BEGIN, IN> chain, BEGIN object, ResourceFactory resourceFactory) throws AssemblyLineException {
		return execute(chain, object, null, resourceFactory);
	}

}
