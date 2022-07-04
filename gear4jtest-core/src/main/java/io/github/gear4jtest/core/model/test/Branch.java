package io.github.gear4jtest.core.model.test;

public class Branch<U, S, T extends Base<S, S, V, ?>, V extends Base<S, S, ?, ?>> extends Base<U, S, T, V> {

	private Stepa<U, S, Branch<S, S, T, V>, ?> step;

	private Branches<U, S, Branch<S, S, T, V>, ?> branches;

	private Branches<S, S, V, ?> parent;

	public Branch(Branches<U, S, V, ?> parent) {
		this.parent = (Branches<S, S, V, ?>) parent;
		this.branches = new Branches<>(this);
		init();
	}

	private void init() {
		this.step = new Stepa<>(this);
	}

	public Stepa<U, S, Branch<S, S, T, V>, ?> step() {
		return step;
	}

	public Branches<U, S, Branch<S, S, T, V>, ?> branches() {
		return this.branches;
	}

	public <A> Branch<A, S, T, V> returns(String expression, Class<A> clazz) {
		return (Branch<A, S, T, V>) this;
	}

	public Branches<S, S, V, ?> done() {
		return parent;
	}

}
