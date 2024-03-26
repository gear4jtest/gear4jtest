package io.github.gear4jtest.core.model;

public class BaseRule {

	protected Class<? extends Throwable> type;

	protected BaseRule() {
	}
	
	protected BaseRule(Class<? extends Throwable> type) {
		this.type = type;
	}
	
	public Class<? extends Throwable> getType() {
		return type;
	}

}

