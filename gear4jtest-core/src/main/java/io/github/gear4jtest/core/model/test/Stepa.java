package io.github.gear4jtest.core.model.test;

import java.util.function.Supplier;

import io.github.gear4jtest.core.model.Step;

public class Stepa<U, S, T extends Base<S, S, V, ?>, V extends Base<S, S, ?, ?>> extends Base<U, S, T, V> {

	private Supplier<Step> step;
	
	private Branch<U, S, V, ?> parent;

	public Stepa(Branch<U, S, V, ?> parent) {
	}

	public <OUT> Stepa<OUT, S, T, V> operation(Supplier<Step<U, OUT>> operation) {
		this.step = (Supplier) operation;
		return (Stepa<OUT, S, T, V>) this;
	}

	public <A> Stepa<A, S, T, V> returns(String expression, Class<A> clazz) {
		return (Stepa<A, S, T, V>) this;
	}

	public Branch<U, S, V, ?> done() {
		return parent;
	}

}
