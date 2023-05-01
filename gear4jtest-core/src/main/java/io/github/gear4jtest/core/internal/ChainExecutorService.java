package io.github.gear4jtest.core.internal;

import java.util.Map;

import io.github.gear4jtest.core.context.Gear4jContext;
import io.github.gear4jtest.core.model.ChainModel;

/**
 * Entry class for Gear4j chains execution.
 * 
 * @author quleb
 *
 */
public class ChainExecutorService {
	
	private final ChainerService service;
	private final LineCommander commander;
	
	public ChainExecutorService() {
		this.service = new ChainerService();
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
		AssemblyLine<BEGIN, IN> line = service.buildChain(chain);
		Gear4jContext ctx = new Gear4jContext(context);
		return (IN) commander.command(line, object, ctx);
	}
	
}
