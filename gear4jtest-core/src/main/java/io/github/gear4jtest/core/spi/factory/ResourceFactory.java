package io.github.gear4jtest.core.spi.factory;

@FunctionalInterface
public interface ResourceFactory {
	
	<T> T getResource(Class<T> clazz);

}
