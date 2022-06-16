package io.github.gear4jtest.core.service;

import io.github.gear4jtest.core.model.Chain;

public class ChainExecutorService {
	
	public <BEGIN, IN> IN execute(Chain<BEGIN, IN> chain, BEGIN object) {
//		Object result = object;
//		for (Step step : chain.getSteps()) {
//			result = step.execute(result);
//		}
//		return (IN) result;
		return (IN) object;
	}
	
}
