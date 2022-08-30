//package io.github.gear4jtest.core.model.generics.recursive;
//
//public class Chain<IN, OUT> {
//
//	private BranchesChain<?, IN> branches;
//
//	private Chain() {
//	}
//
//	public static <A> Builder<A, ?> newChain() {
//		return new Builder<>();
//	}
//
//	public static class Builder<IN, OUT> {
//
//		private final Chain<IN, OUT> managedInstance;
//
//		private Builder() {
//			managedInstance = new Chain<>();
//		}
//
//		public <A> Builder<IN, A> assemble(BranchesChain<A, IN> branches) {
//			managedInstance.branches = branches;
//			return (Builder<IN, A>) this;
//		}
//
//		public Chain<IN, OUT> build() {
//			return managedInstance;
//		}
//
//	}
//
//}
