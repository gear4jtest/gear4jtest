package io.github.gear4jtest.core.processor;

import io.github.gear4jtest.core.processor.ProcessorChain.ProcessingProcessorDriver;

@FunctionalInterface
public interface ProcessingProcessor extends BaseProcessor<Void, ProcessingProcessorDriver> {
	
}
