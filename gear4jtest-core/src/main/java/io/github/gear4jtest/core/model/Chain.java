package io.github.gear4jtest.core.model;

import java.util.function.Consumer;

public class Chain<BEGIN, IN> {

	private Branches<BEGIN, IN, ?> branches;
	
	private Chain() {
	}
	
	public static <A> Builder<A, A> newChain() {
		return new Builder<>();
	}
	
	public static class Builder<BEGIN, IN> {

		private Chain<BEGIN, IN> managedInstance = new Chain<>(); 
		
		private Builder() { }
		
		public Branches.Builder<BEGIN, IN, IN> branches() {
			Consumer<Branches<BEGIN, IN, IN>> callback = obj -> managedInstance.branches = obj;
			return Branches.<BEGIN, IN>newBranches(this, callback);
		}
		
		public Chain<BEGIN, IN> build() {
			return managedInstance;
		}

	}
	
}
