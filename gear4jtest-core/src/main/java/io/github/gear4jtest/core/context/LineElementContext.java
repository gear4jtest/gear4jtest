package io.github.gear4jtest.core.context;

import java.util.Map;

public class LineElementContext {

	private Map<String, Object> chainContext;

	public LineElementContext(Map<String, Object> chainContext) {
		this.chainContext = chainContext;
	}

	public Map<String, Object> getChainContext() {
		return chainContext;
	}
	
}
