package io.github.gear4jtest.external.api.loader;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryClassLoaderRegistryTest {
    @Test
    void clearAlias_shouldRemoveMutableAliasWithoutEvictingConcreteLoader() {
        // Given
        InMemoryClassLoaderRegistry registry = new InMemoryClassLoaderRegistry();
        ClassLoader loader = getClass().getClassLoader();
        registry.register("pipeline:1.0.0:RUN:hash", loader, null);
        registry.setAlias("al/pipeline/RUN/latest", "pipeline:1.0.0:RUN:hash");

        // When
        registry.clearAlias("al/pipeline/RUN/latest");

        // Then
        assertThat(registry.resolveAlias("al/pipeline/RUN/latest"))
                .as("latest alias is invalidated when a new RUN can become latest")
                .isNull();
        assertThat(registry.get("pipeline:1.0.0:RUN:hash"))
                .as("exact compiled versions remain cached after alias invalidation")
                .isSameAs(loader);
    }
}
