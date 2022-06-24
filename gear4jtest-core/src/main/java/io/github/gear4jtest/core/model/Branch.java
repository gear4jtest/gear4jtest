package io.github.gear4jtest.core.model;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class Branch<CHAININ, BRANCHESIN, IN> {

	private final List<Object> steps;
	
	private Branch() {
		this.steps = new ArrayList<>();
	}
	
	public static <A, B> Builder<A, B, B> newBranch(Branches.Builder<A, B, ?> parentBuilder, Consumer<Branch<A, B, B>> callback) {
		return new Builder<>(parentBuilder, callback);
	}
	
	public static class Builder<BEGIN, BRANCHESIN, IN> {

		private Branch<BEGIN, BRANCHESIN, IN> managedInstance = new Branch<>();

		private Consumer<Branch<BEGIN, BRANCHESIN, IN>> callback;
		
		private Branches.Builder<BEGIN, BRANCHESIN, ?> parentBuilder;

		private Builder(Branches.Builder<BEGIN, BRANCHESIN, ?> parentBuilder, Consumer<Branch<BEGIN, BRANCHESIN, IN>> callback) {
			this.parentBuilder = parentBuilder;
		}
		
		public Stepa.Builder<BEGIN, BRANCHESIN, IN, ?> step() {
			Consumer<Stepa<BEGIN, BRANCHESIN, IN, ?>> callback = obj -> managedInstance.steps.add(obj);
			return Stepa.<BEGIN, BRANCHESIN, IN>newStep(this, callback);
		}
		
		public Branches.Builder<BEGIN, IN, IN> branches() {
			Consumer<Branches<BEGIN, IN, IN>> callback = obj -> managedInstance.steps.add(obj);
			return Branches.<BEGIN, BRANCHESIN, IN>newBranches(this, callback);
		}

		public <A> Builder<BEGIN, BRANCHESIN, A> returns(String expression, Class<A> clazz) {
			return (Builder<BEGIN, BRANCHESIN, A>) this;
		}
		
		public Branches.Builder<BEGIN, BRANCHESIN, IN> done() {
			callback.accept(managedInstance);
			return (Branches.Builder<BEGIN, BRANCHESIN, IN>) parentBuilder;
		}
		
	}
	
}
