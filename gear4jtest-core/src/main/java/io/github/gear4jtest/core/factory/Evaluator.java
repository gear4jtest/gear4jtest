package io.github.gear4jtest.core.factory;

// Do we really need this ? Could be useful for JSON / XML modules for parameters interpretation 
// but every parameter could normally be translated into either value, supplier or Function<Input (input + context ?), T>
public interface Evaluator {

	<T> T evaluate(String expression, Class<T> targetClazz);

}
