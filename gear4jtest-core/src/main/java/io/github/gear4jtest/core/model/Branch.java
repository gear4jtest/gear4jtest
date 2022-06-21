package io.github.gear4jtest.core.model;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import io.github.gear4jtest.core.model.Stepa.StepBuilder;

public class Branch<BEGIN, IN> {

	private final List<Object> steps;
	
	private Branch() {
		this.steps = new ArrayList<>();
	}
	
	public static <A, B> BranchBuilder<A, B> newBranch(Branches.BranchesBuilder<A, B> parentBuilder, Consumer<Branch<A, B>> callback) {
		return new BranchBuilder<>(parentBuilder, callback);
	}
	
	public static class BranchBuilder<BEGIN, IN> {

		private Branch<BEGIN, IN> managedInstance = new Branch<>();

		private Consumer<Branch<BEGIN, IN>> callback;
		
		private Branches.BranchesBuilder<BEGIN, IN> parentBuilder;

		private BranchBuilder(Branches.BranchesBuilder<BEGIN, IN> parentBuilder, Consumer<Branch<BEGIN, IN>> callback) {
			this.parentBuilder = parentBuilder;
		}
		
		public StepBuilder<BEGIN, IN, ?> step() {
			Consumer<Stepa<BEGIN, IN, ?>> callback = obj -> managedInstance.steps.add(obj);
			return Stepa.<BEGIN, IN>newStep(this, callback);
		}
		
		public Branches.BranchesBuilder<BEGIN, IN> branches() {
			Consumer<Branches<BEGIN, IN>> callback = obj -> managedInstance.steps.add(obj);
			return Branches.<BEGIN, IN>newBranches(this, callback);
		}

		public <A> BranchBuilder<BEGIN, A> returns(String expression, Class<A> clazz) {
			return (BranchBuilder<BEGIN, A>) this;
		}
		
		public Branches.BranchesBuilder<BEGIN, IN> done() {
			callback.accept(managedInstance);
			return parentBuilder;
		}
		
	}
	
}