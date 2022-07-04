package io.github.gear4jtest.core.model;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class Branches implements Element {

	private List<Branch> branches;
	
	private Branches() {
		this.branches = new ArrayList<>();
	}

	public static <A, B, C> Builder2<A, B, C, C> newBranches(Branch.ChainBuilder<A, B, C> branchBuilder, Consumer<Branches> callback) {
		return new Builder2<>(branchBuilder, callback);
	}

	public static <A> ChainBuilder<A, A> newBranches(Chain.Builder<A, ?> chainBuilder, Consumer<Branches> callback) {
		return new ChainBuilder<>(chainBuilder, callback);
	}
	

//	public static <A> SimpleBuilder<A, A> newBranches(Chain.Builder<A, ?> chainBuilder, Consumer<Branches> callback) {
//		return new SimpleBuilder<>(chainBuilder, callback);
//	}
	
	public static class SimpleBuilder<PARENT, IN> {

		private Branches managedInstance = new Branches();
		
		private Consumer<Branches> callback;
		private Chain.Builder<PARENT, ?> chainBuilder;
		
		private SimpleBuilder(Chain.Builder<PARENT, ?> chainBuilder, Consumer<Branches> callback) {
			this.chainBuilder = chainBuilder;
			this.callback = callback;
		}

		public Branch.SimpleBuilder<PARENT, PARENT> branch() {
			Consumer<Branch> callback = obj -> managedInstance.branches.add(obj);
			return Branch.<PARENT>newBranch(this, callback);
		}
		
		public <A> SimpleBuilder<PARENT, A> returns(String expression, Class<A> clazz) {
			return (SimpleBuilder<PARENT, A>) this;
		}

		public Chain.Builder<PARENT, IN> done() {
			callback.accept(managedInstance);
			return (Chain.Builder<PARENT, IN>) chainBuilder;
		}
		
	}
	
	public static class ChainBuilder<CHAININ, IN> {

		private Branches managedInstance = new Branches();
		
		private Consumer<Branches> callback;
		private Chain.Builder<CHAININ, ?> chainBuilder;
		
		private ChainBuilder(Chain.Builder<CHAININ, ?> chainBuilder, Consumer<Branches> callback) {
			this.chainBuilder = chainBuilder;
			this.callback = callback;
		}

		public Branch.ChainBuilder<CHAININ, IN, IN> branch() {
			Consumer<Branch> callback = obj -> managedInstance.branches.add(obj);
			return Branch.<CHAININ, IN>newBranch(this, callback);
		}
		
		public <A> ChainBuilder<CHAININ, A> returns(String expression, Class<A> clazz) {
			return (ChainBuilder<CHAININ, A>) this;
		}

		public Chain.Builder<CHAININ, IN> done() {
			callback.accept(managedInstance);
			return (Chain.Builder<CHAININ, IN>) chainBuilder;
		}
		
	}
	
	public static class Builder2<CHAININ, BRANCHESIN, PARENTIN, IN> {

		private Branches managedInstance = new Branches();
		
		private Consumer<Branches> callback;
		private Branch.ChainBuilder<CHAININ, BRANCHESIN, PARENTIN> branchBuilder;
		
		private Builder2(Branch.ChainBuilder<CHAININ, BRANCHESIN, PARENTIN> parentBuilder, Consumer<Branches> callback) {
			this.branchBuilder = parentBuilder;
			this.callback = callback;
		}

		public Branch.Builder2<CHAININ, BRANCHESIN, PARENTIN, IN, IN> branch() {
			Consumer<Branch> callback = obj -> managedInstance.branches.add(obj);
			return Branch.<CHAININ, BRANCHESIN, PARENTIN, IN>newBranch(this, callback);
		}
		
		public <A> Builder2<CHAININ, BRANCHESIN, A, A> returns(String expression, Class<A> clazz) {
			return (Builder2<CHAININ, BRANCHESIN, A, A>) this;
		}

		public Branch.ChainBuilder<CHAININ, BRANCHESIN, PARENTIN> done() {
			callback.accept(managedInstance);
			return (Branch.ChainBuilder<CHAININ, BRANCHESIN, PARENTIN>) branchBuilder;
		}
		
	}
	
}