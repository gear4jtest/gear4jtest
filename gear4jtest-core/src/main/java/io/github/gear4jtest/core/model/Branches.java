package io.github.gear4jtest.core.model;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class Branches<BEGIN, IN> implements Element {

	private List<Branch<BEGIN, IN>> branches;
	
	private Branches() {
		this.branches = new ArrayList<>();
	}

	public static <A, B> BranchesBuilder<A, B> newBranches(Branch.BranchBuilder<A, B> branchBuilder, Consumer<Branches<A, B>> callback) {
		return new BranchesBuilder<>(branchBuilder, null, null);
	}

	public static <A, B> BranchesBuilder<A, B> newBranches(Chain.Builder<A, B> chainBuilder, Consumer<Branches<A, B>> callback) {
		return new BranchesBuilder<>(null, chainBuilder, null);
	}
	
	public static class BranchesBuilder<BEGIN, INOUT> {

		private Branches<BEGIN, INOUT> managedInstance = new Branches<>();
		
		private Consumer<Branches<BEGIN, INOUT>> callback;
		private Branch.BranchBuilder<BEGIN, INOUT> branchBuilder;
		private Chain.Builder<BEGIN, INOUT> chainBuilder;
		
		private BranchesBuilder(Branch.BranchBuilder<BEGIN, INOUT> parentBuilder, Chain.Builder<BEGIN, INOUT> chainBuilder, Consumer<Branches<BEGIN, INOUT>> callback) {
			this.branchBuilder = parentBuilder;
			this.chainBuilder = chainBuilder;
			this.callback = callback;
		}

		public Branch.BranchBuilder<BEGIN, INOUT> branch() {
			Consumer<Branch<BEGIN, INOUT>> callback = obj -> managedInstance.branches.add(obj);
			return Branch.<BEGIN, INOUT>newBranch(this, callback);
		}

		public Branches<BEGIN, INOUT> build() {
			return managedInstance;
		}
		
		public Chain.Builder<BEGIN, INOUT> doneChainBranches() {
			callback.accept(managedInstance);
			return chainBuilder;
		}
		
		public Branch.BranchBuilder<BEGIN, INOUT> doneBranchBranches() {
			callback.accept(managedInstance);
			return branchBuilder;
		}
		
	}
	
}