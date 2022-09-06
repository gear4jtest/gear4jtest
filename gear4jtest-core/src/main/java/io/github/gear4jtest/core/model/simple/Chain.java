package io.github.gear4jtest.core.model.simple;

public class Chain<IN, OUT> {

	private Branches branches;
	
	private Chain() {
	}
	
	public Branches getBranches() {
		return branches;
	}
	
	public static class Builder<IN, OUT> {

		private final Chain<IN, OUT> managedInstance;
		
		Builder() {
			managedInstance = new Chain<>();
		}
		
		public <A> Builder<IN, A> assemble(Branches<IN, A> branches) {
			managedInstance.branches = branches;
			return (Builder<IN, A>) this;
		}

		public Chain<IN, OUT> build() {
			return managedInstance;
		}

	}
	
}
