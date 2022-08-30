package io.github.gear4jtest.core.service;

import io.github.gear4jtest.core.model.AssemblyLine;
import io.github.gear4jtest.core.model.simple.Chain;

public class ChainExecutorService {
	
	private ChainerService service = new ChainerService();
	private LineCommander commander = new LineCommander();
	
	// Ici retourner un bean particulier
	// ChainExecutionResult<IN> qui contiendrait le retour, de type IN, s'il y a eu erreur etc...
	public <BEGIN, IN> IN execute(Chain<BEGIN, IN> chain, BEGIN object) {
		AssemblyLine<BEGIN, IN> line = service.execute(chain);
		return (IN) commander.command(line, object);
	}
	
}
