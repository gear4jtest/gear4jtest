package io.github.gear4jtest.core.factory;

@FunctionalInterface
public interface ResourceFactory {
	
	<T> T getResource(Class<T> clazz);

}
