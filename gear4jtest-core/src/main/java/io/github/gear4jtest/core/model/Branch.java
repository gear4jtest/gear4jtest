package io.github.gear4jtest.core.model;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class Branch<BEGIN, IN, OUT> {

	private final List<Object> steps;
	
	private Branch() {
		this.steps = new ArrayList<>();
	}
	
	public static <A, B, C> Builder<A, B, C> newBranch(Branches.Builder<A, B, C> parentBuilder, Consumer<Branch<A, B, C>> callback) {
		return new Builder<>(parentBuilder, callback);
	}
	
	public static class Builder<BEGIN, IN, OUT> {

		private Branch<BEGIN, IN, OUT> managedInstance = new Branch<>();

		private Consumer<Branch<BEGIN, IN, OUT>> callback;
		
		private Branches.Builder<BEGIN, IN, OUT> parentBuilder;

		private Builder(Branches.Builder<BEGIN, IN, OUT> parentBuilder, Consumer<Branch<BEGIN, IN, OUT>> callback) {
			this.parentBuilder = parentBuilder;
		}
		
		public Stepa.Builder<BEGIN, IN, OUT, ?> step() {
			Consumer<Stepa<BEGIN, IN, OUT, ?>> callback = obj -> managedInstance.steps.add(obj);
			return Stepa.<BEGIN, IN, OUT>newStep(this, callback);
		}
		
		public Branches.Builder<BEGIN, OUT, OUT> branches() {
			Consumer<Branches<BEGIN, OUT, OUT>> callback = obj -> managedInstance.steps.add(obj);
			return Branches.<BEGIN, OUT, OUT>newBranches(this, callback);
		}

		public <A> Builder<BEGIN, IN, A> returns(String expression, Class<A> clazz) {
			return (Builder<BEGIN, IN, A>) this;
		}
		
		public Branches.Builder<BEGIN, IN, IN> done() {
			callback.accept(managedInstance);
			return (Branches.Builder<BEGIN, IN, IN>) parentBuilder;
		}
		
	}
	
}
