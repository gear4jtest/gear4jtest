package io.github.gear4jtest.core.processor;

import io.github.gear4jtest.core.internal.Gear4jContext;
import io.github.gear4jtest.core.internal.LineElement;
import io.github.gear4jtest.core.processor.ProcessorChain.BaseProcessorDrivingElement;

@FunctionalInterface
public interface BaseProcessor<T extends LineElement, U extends BaseProcessorDrivingElement<T>> {
	
	void process(Object input, T currentElement, Gear4jContext context, U chainDriver);

}
