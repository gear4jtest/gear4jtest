package io.github.gear4jtest.core.model.simple;

import java.util.function.Supplier;

import io.github.gear4jtest.core.model.Step;

public class Stepa<IN, OUT> {
	
	private Supplier<Step<IN, OUT>> operation;

	private Stepa() {
	}
	
	public Supplier<Step<IN, OUT>> getOperation() {
		return operation;
	}
	
	public static <A> Builder<A, ?> newStep() {
		return new Builder<>();
	}

	public static <A> Builder<A, A> newStep(Branch.Builder<?, A> branchBuilder) {
		return new Builder<>();
	}

	public static class Builder<IN, OUT> {

		private final Stepa<IN, OUT> managedInstance;
		
		private Builder() {
			managedInstance = new Stepa<>();
		}
		
		public <A> Builder<IN, A> operation(Supplier<Step<IN, A>> operation) {
			managedInstance.operation = (Supplier) operation;
			return (Builder<IN, A>) this;
		}

		public Stepa<IN, OUT> build() {
			return managedInstance;
		}

	}
	
}