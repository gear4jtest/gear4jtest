package io.github.gear4jtest.core.model;

public class FatalRule extends BaseRule {

	private FatalRule() {
	}
	
	private FatalRule(Class<? extends Throwable> type) {
		super(type);
	}

	public static class Builder {

		private final FatalRule managedInstance;

		Builder() {
			managedInstance = new FatalRule();
		}

		public FatalRule.Builder type(Class<? extends Throwable> clazz) {
			managedInstance.type = clazz;
			return this;
		}

		public FatalRule build() {
			return managedInstance;
		}

	}
}
