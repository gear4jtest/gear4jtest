package io.github.gear4jtest.core.processor;

import io.github.gear4jtest.core.context.LineElementContext;
import io.github.gear4jtest.core.internal.LineElement;
import io.github.gear4jtest.core.processor.ProcessorChain.ProcessorDrivingElement;

@FunctionalInterface
public interface Processor<T extends LineElement, V extends LineElementContext> extends BaseProcessor<T, ProcessorDrivingElement<T>, V> {
	
}
