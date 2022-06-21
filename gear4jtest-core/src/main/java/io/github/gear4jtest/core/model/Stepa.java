package io.github.gear4jtest.core.model;

import java.util.function.Consumer;
import java.util.function.Supplier;

import io.github.gear4jtest.core.model.Branch.BranchBuilder;

public class Stepa<BEGIN, IN, OUT> {

	private Supplier<Step<IN, OUT>> step;
	
	public Stepa(Supplier<Step<IN, OUT>> step) {
		this.step = step;
	}
	
	private Stepa() {
	}

	public static <BEGIN, IN> StepBuilder<BEGIN, IN, ?> newStep(BranchBuilder<BEGIN, IN> parentBuilder, Consumer<Stepa<BEGIN, IN, ?>> callback) {
		return new StepBuilder<>(parentBuilder, callback);
	}
	
	public static class StepBuilder<BEGIN, IN, OUT> {

		private Stepa<BEGIN, IN, OUT> stepa = new Stepa<>();
		
		private Consumer<Stepa<BEGIN, IN, ?>> callback;
		
		private BranchBuilder<BEGIN, IN> parentBuilder;
		
		private StepBuilder(BranchBuilder<BEGIN, IN> parentBuilder, Consumer<Stepa<BEGIN, IN, ?>> callback) {
			this.parentBuilder = parentBuilder;
		}

		public <A> StepBuilder<BEGIN, IN, A> operation(Supplier<Step<IN, A>> step) {
			stepa.step = (Supplier) step;
			return (StepBuilder<BEGIN, IN, A>) this;
		}
		
		public <A> StepBuilder<BEGIN, IN, A> returns(String expression, Class<A> clazz) {
			return (StepBuilder<BEGIN, IN, A>) this;
		}
		
		public <A> BranchBuilder<BEGIN, OUT> done() {
			callback.accept(stepa);
			return (BranchBuilder<BEGIN, OUT>) parentBuilder;
		}

		public Stepa<BEGIN, IN, OUT> build() {
			return stepa;
		}
		
	}
	
}
