package io.github.gear4jtest.core.model.simple;

import java.util.ArrayList;
import java.util.List;

public class Branches<IN, OUT> {
	
	private List<Branch> branches;

	private Branches() {
		this.branches = new ArrayList<>();
	}
	
	public List<Branch> getBranches() {
		return branches;
	}

	public static class Builder<IN, OUT> {

		private final Branches<IN, OUT> managedInstance;
		
		Builder() {
			managedInstance = new Branches<>();
		}
		
		public Builder<IN, OUT> withBranch(Branch<IN, ?> branch) {
			managedInstance.branches.add(branch);
			return (Builder<IN, OUT>) this;
		}
		
		public <A> Builder<IN, A> returns(String expression, Class<A> clazz) {
			return (Builder<IN, A>) this;
		}

		public Branches<IN, OUT> build() {
			return managedInstance;
		}

	}
	
}
