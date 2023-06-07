package io.github.gear4jtest.core.processor;

import io.github.gear4jtest.core.context.StepExecution;
import io.github.gear4jtest.core.internal.Item;
import io.github.gear4jtest.core.processor.ProcessorChain.BaseProcessorDriver;

@FunctionalInterface
public interface BaseProcessor<T, U extends BaseProcessorDriver> {
	
	ProcessorResult process(Item input, T processorModel, U chainDriver, StepExecution context);

}
