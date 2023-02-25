package io.github.gear4jtest.core.model;

import java.util.ArrayList;
import java.util.List;

public class BranchesModel<IN, OUT> {
	
	private List<BranchModel> branches;

	private BranchesModel() {
		this.branches = new ArrayList<>();
	}
	
	public List<BranchModel> getBranches() {
		return branches;
	}

	public static class Builder<IN, OUT> {

		private final BranchesModel<IN, OUT> managedInstance;
		
		Builder() {
			managedInstance = new BranchesModel<>();
		}
		
		public Builder<IN, OUT> withBranch(BranchModel<IN, ?> branch) {
			managedInstance.branches.add(branch);
			return (Builder<IN, OUT>) this;
		}
		
		public <A> Builder<IN, A> returns(String expression, Class<A> clazz) {
			return (Builder<IN, A>) this;
		}

		public BranchesModel<IN, OUT> build() {
			return managedInstance;
		}

	}
	
}
