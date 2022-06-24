package io.github.gear4jtest.core.model;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class Branches<CHAININ, BRANCHESIN, IN> implements Element {

	private List<Branch<CHAININ, BRANCHESIN, ?>> branches;
	
	private Branches() {
		this.branches = new ArrayList<>();
	}

	public static <A, B, C> Builder<A, B, C> newBranches(Branch.Builder<A, ?, C> branchBuilder, Consumer<Branches<A, B, C>> callback) {
		return new Builder<>(branchBuilder, null, null);
	}

	public static <A, B> Builder<A, B, B> newBranches(Chain.Builder<A, B> chainBuilder, Consumer<Branches<A, B, B>> callback) {
		return new Builder<>(null, chainBuilder, null);
	}
	
	public static class Builder<CHAININ, BRANCHESIN, IN> {

		private Branches<CHAININ, BRANCHESIN, IN> managedInstance = new Branches<>();
		
		private Consumer<Branches<CHAININ, BRANCHESIN, IN>> callback;
		private Branch.Builder<CHAININ, ?, IN> branchBuilder;
		private Chain.Builder<CHAININ, IN> chainBuilder;
		
		private Builder(Branch.Builder<CHAININ, ?, IN> parentBuilder, Chain.Builder<CHAININ, IN> chainBuilder, Consumer<Branches<CHAININ, BRANCHESIN, IN>> callback) {
			this.branchBuilder = parentBuilder;
			this.chainBuilder = chainBuilder;
			this.callback = callback;
		}

		public Branch.Builder<CHAININ, BRANCHESIN, IN> branch() {
			Consumer<Branch<CHAININ, BRANCHESIN, IN>> callback = obj -> managedInstance.branches.add(obj);
			return Branch.<CHAININ, BRANCHESIN, IN>newBranch(this, callback);
		}
		
		public <A> Builder<CHAININ, BRANCHESIN, A> returns(String expression, Class<A> clazz) {
			return (Builder<CHAININ, BRANCHESIN, A>) this;
		}

		public Branches<CHAININ, BRANCHESIN, IN> build() {
			return managedInstance;
		}
		
		public Chain.Builder<CHAININ, IN> doneChainBranches() {
			callback.accept(managedInstance);
			return chainBuilder;
		}
		
		public Branch.Builder<CHAININ, BRANCHESIN, IN> doneBranchBranches() {
			callback.accept(managedInstance);
			return (Branch.Builder<CHAININ, BRANCHESIN, IN>) branchBuilder;
		}
		
	}
	
}