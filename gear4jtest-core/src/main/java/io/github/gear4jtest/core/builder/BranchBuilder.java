package io.github.gear4jtest.core.builder;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import io.github.gear4jtest.core.model.Branch;

public class BranchBuilder<BEGIN, IN> implements Builder<Branch<BEGIN, IN>> {

	List<Builder> elements = new ArrayList<>();
	
	BranchesBuilder parentBuilder;

	private BranchBuilder(BranchesBuilder parentBuilder) {
		this.parentBuilder = parentBuilder;
	}
	
	public static <A, B> BranchBuilder<A, B> newBranch(BranchesBuilder parentBuilder) {
		return new BranchBuilder<>(parentBuilder);
	}

	public StepBuilder<BEGIN, BEGIN> step() {
		StepBuilder<BEGIN, BEGIN> builder = StepBuilder.<BEGIN>newStep(this);
		elements.add(builder);
		return builder;
	}
	
	public <A> BranchBuilder<BEGIN, A> step(StepBuilder<IN, A> step) {
		elements.add(step);
		return (BranchBuilder<BEGIN, A>) this;
	}
	
	public BranchesBuilder<BEGIN, IN> branches() {
		BranchesBuilder<BEGIN, IN> builder = BranchesBuilder.<BEGIN, IN>newBranches(this);
		elements.add(builder);
		return builder;
	}

	@Override
	public Branch build() {
		return new Branch<>(elements.stream()
				.map(Builder::build)
				.collect(Collectors.toList()));
	}
	
	public <A> BranchBuilder<BEGIN, A> returns(String expression, Class<A> clazz) {
		return (BranchBuilder<BEGIN, A>) this;
	}
	
	public BranchesBuilder<BEGIN, IN> done() {
		return parentBuilder;
	}
	
}