package io.github.gear4jtest.core.processor;

import io.github.gear4jtest.core.internal.Item;

@FunctionalInterface
public interface BaseProcessor<T, U, V> {

	void process(Item input, T operatorModel, U customModel, V executionContext);

}
