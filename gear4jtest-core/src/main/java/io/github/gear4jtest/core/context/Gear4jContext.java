package io.github.gear4jtest.core.context;

import java.util.Map;

public class Gear4jContext {

	private Map<String, Object> chainContext;

	public Gear4jContext(Map<String, Object> chainContext) {
		this.chainContext = chainContext;
	}

	public Map<String, Object> getChainContext() {
		return chainContext;
	}
	
}
