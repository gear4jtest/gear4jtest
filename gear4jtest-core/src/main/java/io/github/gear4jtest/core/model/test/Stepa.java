package io.github.gear4jtest.core.model.test;

import java.util.function.Supplier;

import io.github.gear4jtest.core.model.Step;

public class Stepa<U, S, T extends Base<S, ?, ?>> extends Base<U, S, T> {

	private Supplier<Step> step;

	private T parent;

	public Stepa(T parent) {
		this.parent = parent;
	}

	public <OUT> Stepa<OUT, OUT, T> operation(Supplier<Step<U, OUT>> operation) {
		this.step = (Supplier) operation;
		return (Stepa<OUT, OUT, T>) this;
	}

	public <A> Stepa<A, S, T> returns(String expression, Class<A> clazz) {
		return (Stepa<A, S, T>) this;
	}

	public T done() {
		return parent;
	}

}
