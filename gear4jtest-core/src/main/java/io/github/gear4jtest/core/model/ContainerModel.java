//package io.github.gear4jtest.core.model;
//
//import java.util.ArrayList;
//import java.util.List;
//import java.util.function.BiFunction;
//import java.util.function.Function;
//
//public class ContainerModel<IN, OUT> extends BaseLineModel<IN, OUT> {
//
//	private List<BaseLineModel<?, ?>> children;
//	private Function<?, ?> func;
//	private BiFunction<?, ?, ?> biFunc;
//
//	private ContainerModel() {
//		this.children = new ArrayList<>();
//	}
//
//	public List<BaseLineModel<?, ?>> getChildren() {
//		return children;
//	}
//
//	public static class Builder<IN, OUT> {
//
//		private final ContainerModel<IN, OUT> managedInstance;
//
//		Builder() {
//			managedInstance = new ContainerModel<>();
//		}
//
//		public <START, B> Builder<START, OUT> withSubLine(BaseLineModel<START, B> startingElement) {
//			managedInstance.children.add(startingElement);
//			return (Builder<START, OUT>) this;
//		}
//
//		public <START, B, C> Builder<START, C> withSubLineAndReturns(BaseLineModel<START, B> startingElement, Function<B, C> func) {
//			managedInstance.children.add(startingElement);
//			managedInstance.func = func;
//			return (Builder<START, C>) this;
//		}
//
//		public <START, B> Builder<START, OUT> withTwoSubLines(BaseLineModel<START, B> startingElement, BaseLineModel<IN, B> startingElement2) {
//			managedInstance.children.add(startingElement);
//			managedInstance.children.add(startingElement2);
//			return (Builder<START, OUT>) this;
//		}
//
//		public <START, B, C, D> Builder<START, C> withTwoSubLinesAndReturns(BaseLineModel<START, B> startingElement, BaseLineModel<START, C> startingElement2, BiFunction<B, C, D> func) {
//			managedInstance.children.add(startingElement);
//			managedInstance.biFunc = func;
//			return (Builder<START, C>) this;
//		}
//
//		public ContainerModel<IN, OUT> build() {
//			return managedInstance;
//		}
//
//	}
//
//}
