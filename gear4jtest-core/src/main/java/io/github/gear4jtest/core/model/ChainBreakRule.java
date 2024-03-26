package io.github.gear4jtest.core.model;

public class ChainBreakRule extends BaseRule {

	private ChainBreakRule() {
	}
	
	private ChainBreakRule(Class<? extends Throwable> type) {
		super(type);
	}

	public static class Builder {

		private final ChainBreakRule managedInstance;

		Builder() {
			managedInstance = new ChainBreakRule();
		}

		public ChainBreakRule.Builder type(Class<? extends Throwable> clazz) {
			managedInstance.type = clazz;
			return this;
		}

		public ChainBreakRule build() {
			return managedInstance;
		}

	}
}

