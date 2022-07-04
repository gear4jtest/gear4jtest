package io.github.gear4jtest.core.model.test;

public class Branches<U, S, T extends Base<S, S, V, ?>, V extends Base<S, S, ?, ?>> extends Base<U, S, T, V> {

	private Branch<S, S, V, ?> parent;

	public Branches(Branch<U, S, V, ?> parent) {
		this.parent = (Branch<S, S, V, ?>) parent;
	}

	public Branch<U, S, Branches<S, S, T, V>, ?> branch() {
		return new Branch<>(this);
	}

	public <A> Branches<A, S, T, V> returns(String expression, Class<A> clazz) {
		return (Branches<A, S, T, V>) this;
	}

	public Branch<S, S, V, ?> done() {
		return parent;
	}

}
