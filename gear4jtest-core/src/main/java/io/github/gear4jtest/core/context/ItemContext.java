package io.github.gear4jtest.core.context;

import java.util.HashMap;
import java.util.Map;

public class ItemContext {

	private Map<String, Object> itemContext;
	
	public ItemContext clone() {
		return new ItemContext(new HashMap<>(itemContext));
	}

	public ItemContext(Map<String, Object> context) {
		this.itemContext = context;
	}
	
	public ItemContext() {
		this.itemContext = new HashMap<>();
	}

	public Map<String, Object> getItemContext() {
		return itemContext;
	}

}
