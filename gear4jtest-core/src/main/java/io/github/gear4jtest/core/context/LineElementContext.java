package io.github.gear4jtest.core.context;

import java.util.HashMap;
import java.util.Map;

public class LineElementContext {

	private Map<String, Object> context;

	public LineElementContext() {
		this.context = new HashMap<>();
	}

	public LineElementContext(Map<String, Object> context) {
		this.context = context;
	}

	public LineElementContext clone() {
		return new LineElementContext(new HashMap<>(context));
	}

	public Map<String, Object> getContext() {
		return context;
	}

}
