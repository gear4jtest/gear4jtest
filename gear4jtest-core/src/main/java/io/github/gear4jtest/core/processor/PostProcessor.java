package io.github.gear4jtest.core.processor;

import io.github.gear4jtest.core.context.StepProcessingContext;
import io.github.gear4jtest.core.internal.StepLineElement;

@FunctionalInterface
public interface PostProcessor extends Processor<StepLineElement, StepProcessingContext> {
	
}
