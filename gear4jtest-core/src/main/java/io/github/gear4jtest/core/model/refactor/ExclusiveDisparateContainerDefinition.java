//package io.github.gear4jtest.core.model.refactor;
//
//import java.util.ArrayList;
//import java.util.List;
//import java.util.function.BiFunction;
//import java.util.function.Function;
//
//import io.github.gear4jtest.core.model.refactor.FormerContainerDefinition.Builder;
//
//public class ExclusiveDisparateContainerDefinition<IN, OUT> extends OperationDefinition<IN, OUT> {
//
//	private List<LineDefinition<?, ?>> subLines;
//
//	private ExclusiveDisparateContainerDefinition() {
//		this.subLines = new ArrayList<>();
//	}
//
//	public List<LineDefinition<?, ?>> getChildren() {
//		return subLines;
//	}
//
//	public static class Builder<IN, OUT> {
//
//		private final ExclusiveDisparateContainerDefinition<IN, OUT> managedInstance;
//
//		public Builder() {
//			managedInstance = new ExclusiveDisparateContainerDefinition<>();
//		}
//
//		public Builder<IN, OUT> withIfLine(LineDefinition<IN, OUT> startingElement) {
//			managedInstance.subLines.add(startingElement);
//			return this;
//		}
//
//		public <START, B> Builder<START, OUT> withSubLine(LineDefinition<START, B> startingElement) {
//			managedInstance.subLines.add(startingElement);
//			return (Builder<START, OUT>) this;
//		}
//
//		public <START, B, C> Builder<START, C> withSubLineAndReturns(LineDefinition<START, B> startingElement, Function<B, C> func) {
//			managedInstance.subLines.add(startingElement);
//			managedInstance.func = func;
//			return (Builder<START, C>) this;
//		}
//
//		public <START, B> Builder<START, OUT> withSubLines(LineDefinition<START, B> startingElement, LineDefinition<IN, B> startingElement2) {
//			managedInstance.subLines.add(startingElement);
//			managedInstance.subLines.add(startingElement2);
//			return (Builder<START, OUT>) this;
//		}
//
//		public <START, B, C, D> Builder<START, C> withSubLinesAndReturns(LineDefinition<START, B> startingElement, LineDefinition<START, C> startingElement2, BiFunction<B, C, D> func) {
//			managedInstance.subLines.add(startingElement);
//			managedInstance.subLines.add(startingElement2);
//			managedInstance.biFunc = func;
//			return (Builder<START, C>) this;
//		}
//
//		// Optional : automatic identity line added on else behaviour
//		// fatalOnNoExecution ?
//		public ExclusiveDisparateContainerDefinition<IN, OUT> withElseLine(LineDefinition<IN, OUT> startingElement) {
//			managedInstance.subLines.add(startingElement);
//			return this.managedInstance;
//		}
//
//	}
//
//}
