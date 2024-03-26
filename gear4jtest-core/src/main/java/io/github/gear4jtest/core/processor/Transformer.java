package io.github.gear4jtest.core.processor;

@FunctionalInterface
public interface Transformer<IN, OUT> {
	
	OUT tranform(IN input);
	
}
