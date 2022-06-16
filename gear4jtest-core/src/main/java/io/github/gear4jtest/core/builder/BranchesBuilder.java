package io.github.gear4jtest.core.builder;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import io.github.gear4jtest.core.model.Branches;

public class BranchesBuilder<BEGIN, INOUT> implements Builder<Branches<BEGIN, INOUT>> {

	List<BranchBuilder> branches = new ArrayList<>();
	
	BranchBuilder branchesBuilder;
	ChainBuilder chainBuilder;
	
	private BranchesBuilder(BranchBuilder parentBuilder, ChainBuilder chainBuilder) {
		this.branchesBuilder = parentBuilder;
		this.chainBuilder = chainBuilder;
	}
	
	public static <A, B> BranchesBuilder<A, B> newBranches(BranchBuilder chainBuilder) {
		return new BranchesBuilder<>(chainBuilder, null);
	}

	public static <A> BranchesBuilder<A, A> newBranches(ChainBuilder chainBuilder) {
		return new BranchesBuilder<>(null, chainBuilder);
	}

	public BranchBuilder<BEGIN, INOUT> branch() {
		BranchBuilder<BEGIN, INOUT> branch = BranchBuilder.<BEGIN, INOUT>newBranch(this);
		branches.add(branch);
		return branch;
	}

	@Override
	public Branches<BEGIN, INOUT> build() {
		return new Branches<>(branches.stream()
				.map(BranchBuilder::build)
				.collect(Collectors.toList()));
	}
	
	public ChainBuilder<BEGIN, INOUT> doneChainBranches() {
		return chainBuilder;
	}
	
	public BranchBuilder<BEGIN, INOUT> doneBranchBranches() {
		return branchesBuilder;
	}
	
}