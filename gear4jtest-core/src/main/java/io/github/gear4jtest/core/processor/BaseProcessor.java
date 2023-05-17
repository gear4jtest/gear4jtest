package io.github.gear4jtest.core.processor;

import io.github.gear4jtest.core.context.LineElementContext;
import io.github.gear4jtest.core.internal.Item;
import io.github.gear4jtest.core.internal.LineElement;
import io.github.gear4jtest.core.processor.ProcessorChain.BaseProcessorDrivingElement;

@FunctionalInterface
public interface BaseProcessor<T extends LineElement, U extends BaseProcessorDrivingElement<T>, V extends LineElementContext> {
	
	ProcessorResult process(Item input, T currentElement, U chainDriver, V context);

}
