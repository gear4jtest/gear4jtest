package io.github.gear4jtest.core.model;

public class IgnoreRule extends BaseRule {

	private IgnoreRule() {
	}
	
	private IgnoreRule(Class<? extends Throwable> type) {
		super(type);
	}

	public static class Builder {

		private final IgnoreRule managedInstance;

		Builder() {
			managedInstance = new IgnoreRule();
		}

		public IgnoreRule.Builder type(Class<? extends Throwable> clazz) {
			managedInstance.type = clazz;
			return this;
		}

		public IgnoreRule build() {
			return managedInstance;
		}

	}
}

