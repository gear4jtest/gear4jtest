//package io.github.gear4jtest.core.processor;
//
//import io.github.gear4jtest.core.internal.Item;
//
//@FunctionalInterface
//public interface UserModelBasedBaseProcessor<T, U, V> extends BaseProcessor<T, V> {
//
//	default void process(Item input, T operatorModel, V executionContext) {
//		process(input, operatorModel, (U) executionContext.getClass(), executionContext);
//	}
//	
//	void process(Item input, T operatorModel, U customModel, V executionContext);
//
//}
