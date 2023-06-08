package io.github.gear4jtest.core.model;

public class GlobalOnError extends BaseOnError {

	GlobalOnError() {
		super();
	}

	public static class Builder {

		protected final GlobalOnError managedInstance;

		Builder() {
			managedInstance = new GlobalOnError();
		}

		public UnsafeOnError.Builder<GlobalOnError> rule(ChainBreakRule rule) {
			managedInstance.rules.add(rule);
			return new UnsafeOnError.Builder<>(managedInstance);
		}
		
		public UnsafeOnError.Builder<GlobalOnError> rule(IgnoreRule rule) {
			managedInstance.rules.add(rule);
			return new UnsafeOnError.Builder<>(managedInstance);
		}
		
		public <X extends BaseRule> GlobalOnError.Builder rule(X rule) {
			managedInstance.rules.add(rule);
			return this;
		}

		public GlobalOnError build() {
			return managedInstance;
		}

	}

}