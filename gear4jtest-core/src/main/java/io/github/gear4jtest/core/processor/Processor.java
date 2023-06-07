package io.github.gear4jtest.core.processor;

import io.github.gear4jtest.core.processor.ProcessorChain.ProcessorDriver;

@FunctionalInterface
public interface Processor<T> extends BaseProcessor<T, ProcessorDriver> {
	
}
