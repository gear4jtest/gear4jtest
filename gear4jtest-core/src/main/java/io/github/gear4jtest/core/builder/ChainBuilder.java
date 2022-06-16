package io.github.gear4jtest.core.builder;

import io.github.gear4jtest.core.model.Chain;

public class ChainBuilder<BEGIN, OUT> implements Builder<Chain<BEGIN, OUT>> {

	private BranchesBuilder branchesBuilder;

	private ChainBuilder() { }
	
	public static <A> ChainBuilder<A, ?> newChain() {
		return new ChainBuilder<>();
	}
	
	public BranchesBuilder<BEGIN, BEGIN> branches() {
		branchesBuilder = BranchesBuilder.<BEGIN>newBranches(this);
		return branchesBuilder;
	}
	
	@Override
	public Chain<BEGIN, OUT> build() {
		return new Chain<>(branchesBuilder.build());
	}

}
