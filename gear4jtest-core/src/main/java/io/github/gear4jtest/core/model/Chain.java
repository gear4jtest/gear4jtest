package io.github.gear4jtest.core.model;

import java.util.function.Consumer;

public class Chain<IN, OUT> {

	private Branches<IN, IN, ?> branches;
	
	private Chain() {
	}
	
	public static <A> Builder<A, ?> newChain() {
		return new Builder<>();
	}
	
	public static class Builder<IN, OUT> {

		private Chain<IN, OUT> managedInstance = new Chain<>(); 
		
		private Builder() { }
		
		public Branches.Builder<IN, IN, ?> branches() {
			Consumer<Branches<IN, IN, ?>> callback = obj -> managedInstance.branches = obj;
			return Branches.<IN>newBranches(this, callback);
		}
		
		public Chain<IN, OUT> build() {
			return managedInstance;
		}

	}
	
}
