package io.github.gear4jtest.core.processor;

import io.github.gear4jtest.core.processor.ProcessorChain.InvokerDriver;

@FunctionalInterface
public interface Invoker extends BaseProcessor<Void, InvokerDriver> {

//	ProcessorResult invoke(Item input, InvokerDriver chainDriver, StepExecution context);

}
