//package io.github.gear4jtest.core.model.refactor;
//
//import io.github.gear4jtest.core.context.LineOperatorExecution;
//
//import java.util.function.BiPredicate;
//import java.util.function.Function;
//
//public class UnpredictableLineDefinition<X, Y> {
//
//	public static class Builder<IN, OUT> {
//
//		private final LineDefinition.Builder<IN, OUT> managedInstance;
//
//		public Builder(LineDefinition.Builder<IN, OUT> lineDefinition) {
//			managedInstance = lineDefinition;
//		}
//
//		public <A> Builder<IN, A> operator(OperationDefinition<OUT, A> operator) {
//			managedInstance.operator(operator);
//			return (Builder<IN, A>) this;
//		}
//
//		public Builder<IN, OUT> condition(BiPredicate<IN, LineOperatorExecution> condition) {
//			managedInstance.condition(condition);
//			return this;
//		}
//
//		public LineDefinition<IN, Object> build() {
//			return (LineDefinition<IN, Object>) managedInstance.build();
//		}
//
//	}
//
//}
