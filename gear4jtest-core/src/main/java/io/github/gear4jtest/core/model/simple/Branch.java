package io.github.gear4jtest.core.model.simple;

import java.util.ArrayList;
import java.util.List;

public class Branch<IN, OUT> {
	
	private List<Object> children;

	private Branch() {
		this.children = new ArrayList<>();
	}
	
	public List<Object> getChildren() {
		return children;
	}
	
	public static <A> Builder<A, A> newBranch() {
		return new Builder<>();
	}
	
	public static <A> Builder<A, A> newBranch(Branches.Builder<A, ?> branchesBuilder) {
		return new Builder<>();
	}

	public static class Builder<IN, OUT> {

		private final Branch<IN, OUT> managedInstance;
		
		private Builder() {
			managedInstance = new Branch<>();
		}
		
		public <A> Builder<IN, A> withStep(Stepa<OUT, A> step) {
			managedInstance.children.add(step);
			return (Builder<IN, A>) this;
		}
		
		public <A> Builder<IN, A> withBranches(Branches<OUT, A> branch) {
			managedInstance.children.add(branch);
			return (Builder<IN, A>) this;
		}

		public Branch<IN, OUT> build() {
			return managedInstance;
		}

	}
	
}
