package io.github.gear4jtest.core.model;

import java.util.ArrayList;
import java.util.List;

public class BranchModel<IN, OUT> {
	
	private List<Object> children;

	private BranchModel() {
		this.children = new ArrayList<>();
	}
	
	public List<Object> getChildren() {
		return children;
	}

	public static class Builder<IN, OUT> {

		private final BranchModel<IN, OUT> managedInstance;
		
		Builder() {
			managedInstance = new BranchModel<>();
		}
		
		public <A> Builder<IN, A> withStep(OperationModel<OUT, A> step) {
			managedInstance.children.add(step);
			return (Builder<IN, A>) this;
		}
		
		public <A> Builder<IN, A> withBranches(BranchesModel<OUT, A> branch) {
			managedInstance.children.add(branch);
			return (Builder<IN, A>) this;
		}

		public BranchModel<IN, OUT> build() {
			return managedInstance;
		}

	}
	
}
