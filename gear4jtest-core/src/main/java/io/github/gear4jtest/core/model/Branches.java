package io.github.gear4jtest.core.model;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class Branches<CHAININ, PARENTIN, IN> implements Element {

	private List<Branch<CHAININ, PARENTIN, ?>> branches;
	
	private Branches() {
		this.branches = new ArrayList<>();
	}

	public static <A, B, C> Builder<A, C, C> newBranches(Branch.Builder<A, B, C> branchBuilder, Consumer<Branches<A, C, C>> callback) {
		return new Builder<>((Branch.Builder<A, C, C>) branchBuilder, null, null);
	}

	public static <A> Builder<A, A, ?> newBranches(Chain.Builder<A, ?> chainBuilder, Consumer<Branches<A, A, ?>> callback) {
		return new Builder<>(null, chainBuilder, null);
	}
	
	public static class Builder<CHAININ, IN, OUT> {

		private Branches<CHAININ, IN, OUT> managedInstance = new Branches<>();
		
		private Consumer<Branches<CHAININ, IN, OUT>> callback;
		private Branch.Builder<CHAININ, IN, ?> branchBuilder;
		private Chain.Builder<CHAININ, OUT> chainBuilder;
		
		private Builder(Branch.Builder<CHAININ, IN, OUT> parentBuilder, Chain.Builder<CHAININ, OUT> chainBuilder, Consumer<Branches<CHAININ, IN, OUT>> callback) {
			this.branchBuilder = parentBuilder;
			this.chainBuilder = chainBuilder;
			this.callback = callback;
		}

		public Branch.Builder<CHAININ, IN, IN> branch() {
			Consumer<Branch<CHAININ, IN, IN>> callback = obj -> managedInstance.branches.add(obj);
			return Branch.<CHAININ, IN>newBranch(this, callback);
		}
		
		public <A> Builder<CHAININ, IN, A> returns(String expression, Class<A> clazz) {
			return (Builder<CHAININ, IN, A>) this;
		}

		public Branches<CHAININ, IN, OUT> build() {
			return managedInstance;
		}
		
		public Chain.Builder<CHAININ, OUT> doneChainBranches() {
			callback.accept(managedInstance);
			return chainBuilder;
		}
		
		public Branch.Builder<CHAININ, IN, OUT> doneBranchBranches() {
			callback.accept(managedInstance);
			return (Branch.Builder<CHAININ, IN, OUT>) branchBuilder;
		}
		
	}
	
}