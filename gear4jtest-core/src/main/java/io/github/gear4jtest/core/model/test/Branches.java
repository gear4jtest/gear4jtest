package io.github.gear4jtest.core.model.test;

public class Branches<U, S, T extends Base<S, ?, ?>> extends Base<U, S, T> {

	private T parent;

	public Branches(Branch<U, ?, ?> parent) {
		this.parent = (T) parent;
	}

	public Branch<U, U, Branches<U, S, T>> branch() {
		return new Branch<>(this);
	}

	public <A> Branches<A, S, T> returns(String expression, Class<A> clazz) {
		return (Branches<A, S, T>) this;
	}

	public T done() {
		return parent;
	}

}
