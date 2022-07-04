package io.github.gear4jtest.core.model;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class Branch {

	private final List<Object> steps;
	
	private Branch() {
		this.steps = new ArrayList<>();
	}
	
	public static <A> SimpleBuilder<A, A> newBranch(Branches.SimpleBuilder<A, ?> parentBuilder, Consumer<Branch> callback) {
		return new SimpleBuilder<>(parentBuilder, callback);
	}
	
	public static <A, B> ChainBuilder<A, B, B> newBranch(Branches.ChainBuilder<A, B> parentBuilder, Consumer<Branch> callback) {
		return new ChainBuilder<>(parentBuilder, callback);
	}
	
	public static <A, B, C, D> Builder2<A, B, C, D, D> newBranch(Branches.Builder2<A, B, C, D> parentBuilder, Consumer<Branch> callback) {
		return new Builder2<>(parentBuilder, callback);
	}
	
	public static class SimpleBuilder<PARENT, IN> {

		private Branch managedInstance = new Branch();

		private Consumer<Branch> callback;
		
		private Branches.SimpleBuilder<PARENT, ?> parentBuilder;

		private SimpleBuilder(Branches.SimpleBuilder<PARENT, ?> parentBuilder, Consumer<Branch> callback) {
			this.parentBuilder = parentBuilder;
		}
		
		public <A> Branch.SimpleBuilder<PARENT, A> returns(String expression, Class<A> clazz) {
			return (Branch.SimpleBuilder<PARENT, A>) this;
		}		
		public Branches.SimpleBuilder<PARENT, PARENT> done() {
			callback.accept(managedInstance);
			return (Branches.SimpleBuilder<PARENT, PARENT>) parentBuilder;
		}
		
	}
	
	public static class ChainBuilder<BEGIN, BRANCHESIN, IN> {

		private Branch managedInstance = new Branch();

		private Consumer<Branch> callback;
		
		private Branches.ChainBuilder<BEGIN, BRANCHESIN> parentBuilder;

		private ChainBuilder(Branches.ChainBuilder<BEGIN, BRANCHESIN> parentBuilder, Consumer<Branch> callback) {
			this.parentBuilder = parentBuilder;
		}
		
		public Stepa.ChainBuilder<BEGIN, BRANCHESIN, IN, ?> step() {
			Consumer<Stepa> callback = obj -> managedInstance.steps.add(obj);
			return Stepa.<BEGIN, BRANCHESIN, IN>newStep(this, callback);
		}
		
		public Branches.Builder2<BEGIN, BRANCHESIN, IN, IN> branches() {
			Consumer<Branches> callback = obj -> managedInstance.steps.add(obj);
			return Branches.<BEGIN, BRANCHESIN, IN>newBranches(this, callback);
		}
		
		public Branches.ChainBuilder<BEGIN, IN> done() {
			callback.accept(managedInstance);
			return (Branches.ChainBuilder<BEGIN, IN>) parentBuilder;
		}
		
	}
	
	public static class Builder2<BEGIN, BRANCHESIN, PARENTIN, PARENTIN2, IN> {

		private Branch managedInstance = new Branch();

		private Consumer<Branch> callback;
		
		private Branches.Builder2<BEGIN, BRANCHESIN, PARENTIN, PARENTIN2> parentBuilder;

		private Builder2(Branches.Builder2<BEGIN, BRANCHESIN, PARENTIN, PARENTIN2> parentBuilder, Consumer<Branch> callback) {
			this.parentBuilder = parentBuilder;
		}
		
		public Stepa.Builder2<BEGIN, BRANCHESIN, PARENTIN, PARENTIN2, IN, ?> step() {
			Consumer<Stepa> callback = obj -> managedInstance.steps.add(obj);
			return Stepa.<BEGIN, BRANCHESIN, PARENTIN, PARENTIN2, IN>newStep(this, callback);
		}
		
		public Branches.Builder2<BEGIN, BRANCHESIN, PARENTIN, PARENTIN2> done() {
			callback.accept(managedInstance);
			return (Branches.Builder2<BEGIN, BRANCHESIN, PARENTIN, PARENTIN2>) parentBuilder;
		}
		
	}
	
}
