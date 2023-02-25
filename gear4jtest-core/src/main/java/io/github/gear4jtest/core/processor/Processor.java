package io.github.gear4jtest.core.processor;

import io.github.gear4jtest.core.internal.LineElement;
import io.github.gear4jtest.core.processor.ProcessorChain.ProcessorDrivingElement;

@FunctionalInterface
public interface Processor<T extends LineElement> extends BaseProcessor<T, ProcessorDrivingElement<T>> {
	
}
