//package io.github.gear4jtest.core.model.refactor;
//
//import java.util.List;
//
//public class ContainerDefinition<IN, OUT> extends OperationDefinition<IN, OUT> {
//
//	private final List<LineDefinition<?, ?>> subLines;
//	private final ContainerFunction function;
//
//	public ContainerDefinition(List<LineDefinition<?, ?>> subLines, ContainerFunction function) {
//		this.subLines = subLines;
//		this.function = function;
//	}
//
//	public List<LineDefinition<?, ?>> getSubLines() {
//		return subLines;
//	}
//
//	public ContainerFunction getFunction() {
//		return function;
//	}
//
//	@FunctionalInterface
//	public interface ContainerFunction {
//		Object apply(Object... objects);
//	}
//
//}
