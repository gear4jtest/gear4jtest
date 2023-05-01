package io.github.gear4jtest.core.context;

import java.util.Map;

public class ItemContext {

	private Map<String, Object> chainContext;

	public ItemContext(Map<String, Object> chainContext) {
		this.chainContext = chainContext;
	}

	public Map<String, Object> getChainContext() {
		return chainContext;
	}

}
