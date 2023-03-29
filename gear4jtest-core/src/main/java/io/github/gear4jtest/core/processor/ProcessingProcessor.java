package io.github.gear4jtest.core.processor;

import io.github.gear4jtest.core.internal.LineElement;
import io.github.gear4jtest.core.processor.ProcessorChain.ProcessingProcessorDrivingElement;

@FunctionalInterface
public interface ProcessingProcessor<T extends LineElement, V> extends BaseProcessor<T, ProcessingProcessorDrivingElement<T>, V> {
	
}
