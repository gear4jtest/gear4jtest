package io.github.gear4jtest.core.model.test;

public class Branch<U, S, T extends Base<S, ?, ?>> extends Base<U, S, T> {

	private Stepa<U, S, Branch<S, S, T>> step;

	private Branches<U, U, Branch<U, S, T>> branches;

	private T parent;

	public Branch(Branches<U, ?, ?> parent) {
		this.parent = (T) parent;
		this.branches = new Branches<>(this);
		init();
	}

	private void init() {
//		this.step = new Stepa<>(this);
	}

	public Stepa<U, S, Branch<S, S, T>> step() {
		return step;
	}

	public Branches<U, U, Branch<U, S, T>> branches() {
		return this.branches;
	}

	public <A> Branch<A, S, T> returns(String expression, Class<A> clazz) {
		return (Branch<A, S, T>) this;
	}

	public T done() {
		return parent;
	}

}
