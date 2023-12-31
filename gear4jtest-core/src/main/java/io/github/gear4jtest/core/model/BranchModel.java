package io.github.gear4jtest.core.model;

import java.util.ArrayList;
import java.util.List;

public class BranchModel<IN, OUT> extends BaseLineModel<IN, OUT> {

	private List<BaseLineModel<?, ?>> children;

	private BranchModel() {
		this.children = new ArrayList<>();
	}

	public List<BaseLineModel<?, ?>> getChildren() {
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

		public <A> IterableBranchModel.Builder<IN, A, Iterable<A>> withIterableStep(OperationModel<OUT, ? extends Iterable<A>> step) {
			managedInstance.children.add(step);
			return new IterableBranchModel.Builder<IN, A, Iterable<A>>(this);
		}

		public Builder<IN, OUT> withSignal(SignalModel<OUT> signal) {
			managedInstance.children.add(signal);
			return this;
		}

//		public <A, ITERABLE extends Iterable<A> & OUT, B extends OUT & ITERABLE> Builder<IN, A> iterate(OperationModel<?, ITERABLE> builder, OperationModel<?, OUT> builder2) {
//			return (Builder<IN, A>) this;
//		}

		public <A> Builder<IN, A> withBranches(BranchesModel<OUT, A> branch) {
			managedInstance.children.add(branch);
			return (Builder<IN, A>) this;
		}

		public BranchModel<IN, OUT> build() {
			return managedInstance;
		}

	}

	public static class IterableBranchModel<IN, OUT extends Iterable<?>> {

		private List<Object> children;

		private IterableBranchModel() {
			this.children = new ArrayList<>();
		}

		public List<Object> getChildren() {
			return children;
		}

		public static class Builder<IN, A, OUT extends Iterable<A>> {

			private final BranchModel.Builder<IN, OUT> managedInstance;

			Builder(BranchModel.Builder builder) {
				managedInstance = builder;
			}

			public <OP_OUT> BranchModel.Builder<IN, OP_OUT> withStep(OperationModel<OUT, OP_OUT> step) {
				managedInstance.withStep(step);
				return (BranchModel.Builder<IN, OP_OUT>) managedInstance;
			}
			
			public <B> IterableBranchModel.Builder<IN, B, Iterable<B>> withIterableStep(OperationModel<OUT, Iterable<B>> step) {
				managedInstance.withStep(step);
				return (Builder<IN, B, Iterable<B>>) this;
			}

			public Builder<IN, A, OUT> withSignal(SignalModel signal) {
				managedInstance.withSignal(signal);
				return this;
			}

			public BranchModel.Builder<IN, A> iterate() {
				// managedInstance.children.add(new IteratorOperator());
				return (BranchModel.Builder<IN, A>) managedInstance;
			}
			
			public <A> Builder<IN, A, OUT> withBranches(BranchesModel<OUT, A> branch) {
				managedInstance.withBranches(branch);
				return (Builder<IN, A, OUT>) this;
			}

			public BranchModel<IN, OUT> build() {
				return managedInstance.build();
			}

		}

	}
}
