package io.github.gear4jtest.core.api;

import io.github.gear4jtest.core.spi.factory.ResourceFactory;

/** Factory methods for the default Gear4J execution runtime. */
public final class AssemblyLineExecutors {
    private AssemblyLineExecutors() {
    }

    public static AssemblyLineExecutorBuilder builder() {
        return new DefaultAssemblyLineExecutorBuilder();
    }

    public static AssemblyLineExecutor create(ResourceFactory resourceFactory) {
        return builder().resourceFactory(resourceFactory).build();
    }
}
