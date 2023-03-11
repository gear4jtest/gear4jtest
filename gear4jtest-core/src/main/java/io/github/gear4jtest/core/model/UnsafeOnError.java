package io.github.gear4jtest.core.model;

public class UnsafeOnError<T extends BaseOnError> {
	
	private T onError;
	
	public UnsafeOnError(T onError) {
		this.onError = onError;
	}
	
	public T getOnError() {
		return onError;
	}

	public static class Builder<T extends BaseOnError> {
		
		private final UnsafeOnError<T> managedInstance;

		Builder(T onError) {
			managedInstance = new UnsafeOnError<>(onError);
		}

		public UnsafeOnError.Builder<T> rule(BaseRule rule) {
			managedInstance.onError.rules.add(rule);
			return this;
		}

		public UnsafeOnError<T> build() {
			return managedInstance;
		}

	}
	
}