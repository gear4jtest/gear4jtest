package io.github.gear4jtest.core.builder;

import java.util.function.Supplier;

import io.github.gear4jtest.core.model.Step;
import io.github.gear4jtest.core.model.Stepa;

public class StepBuilder<IN, OUT> implements Builder<Stepa<IN, OUT>> {

	private Supplier<Step> step;
	
	private BranchBuilder<IN, ?> parentBuilder;
	
	private StepBuilder(BranchBuilder parentBuilder) {
		this.parentBuilder = parentBuilder;
	}
	
	public static <A> StepBuilder<A, A> newStep(BranchBuilder parentBuilder) {
		return new StepBuilder<>(parentBuilder);
	}
	
	public static <A, B> StepBuilder<A, B> newStep(Class<? extends Step<A, B>> clazz) {
		return new StepBuilder<>(null);
	}

	public <A> StepBuilder<IN, A> operation(Supplier<Step<IN, A>> step) {
		this.step = (Supplier) step;
		this.parentBuilder = (BranchBuilder<IN, A>) parentBuilder;
		return (StepBuilder<IN, A>) this;
	}
	
	public <A> StepBuilder<IN, A> returns(String expression, Class<A> clazz) {
		return (StepBuilder<IN, A>) this;
	}
	
	public <A> BranchBuilder<IN, A> done() {
		return (BranchBuilder<IN, A>) parentBuilder;
	}

	@Override
	public Stepa<IN, OUT> build() {
		return new Stepa(step);
	}
	
}