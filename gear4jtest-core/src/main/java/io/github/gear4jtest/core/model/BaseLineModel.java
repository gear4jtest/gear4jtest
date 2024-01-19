//package io.github.gear4jtest.core.model;
//
//import java.util.List;
//import java.util.function.Function;
//
//public class BaseLineModel<IN, OUT> {
//
//	private BaseLineModel nextElement;
//
//	public BranchModel<IN, ?> withBranch(BranchModel<IN, ?> branch) {
//		nextElement = branch;
//		return branch;
//	}
//
//	public <A> BaseLineModel<IN, A> withStep(OperationModel<OUT, A> step) {
//		nextElement = step;
//		return (BaseLineModel<IN, A>) step;
//	}
//
//	public BaseLineModel<IN, OUT> withSignal(SignalModel<OUT> signal) {
//		nextElement = signal;
//		return (BaseLineModel<IN, OUT>) signal;
//	}
//
//	public <A, B> BaseLineModel<IN, B> iterate(Function<OUT, ? extends Iterable<A>> iterableFunction, BaseLineModel<A, B> nestedElement) {
//		BaseLineModel<?, ?> element = new IteratorModel.Builder<IN>().iterableFunction(iterableFunction).nestedElement(nestedElement).build();
//		nextElement = element;
//		return (BaseLineModel<IN, B>) nestedElement;
//	}
//
////	public <A, ITERABLE extends Iterable<A> & OUT, B extends OUT & ITERABLE> Builder<IN, A> iterate(OperationModel<?, ITERABLE> builder, OperationModel<?, OUT> builder2) {
////		return (Builder<IN, A>) this;
////	}
//
//	public <A> BranchesModel<OUT, A> withBranches(BranchesModel<OUT, A> branch) {
//		nextElement = branch;
//		return branch;
//	}
//
//	public BaseLineModel<IN, ?> getNextElement() {
//		return nextElement;
//	}
//
//}
