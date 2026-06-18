package io.github.gear4jtest.external.api.loader;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryClassLoaderRegistryTest {
    @Test
    void clearAlias_shouldRemoveMutableAliasWithoutEvictingConcreteLoader() {
        // Given
        InMemoryClassLoaderRegistry registry = InMemoryClassLoaderRegistry.builder().build();
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

    @Test
    void register_shouldEvictLeastRecentlyUsedUnaliasedLoaderWhenCapacityIsExceeded() {
        // Given
        InMemoryClassLoaderRegistry registry = InMemoryClassLoaderRegistry.builder().maxLoaders(2).build();
        ClassLoader loader = getClass().getClassLoader();
        registry.register("v1", loader, null);
        registry.register("v2", loader, null);
        registry.get("v1");

        // When
        registry.register("v3", loader, null);

        // Then
        assertThat(registry.get("v1")).as("recently accessed loader is retained").isSameAs(loader);
        assertThat(registry.get("v2")).as("least recently used unaliased loader is evicted").isNull();
        assertThat(registry.get("v3")).as("new loader is registered").isSameAs(loader);
        assertThat(registry.snapshotStats().evictedLoaders()).isEqualTo(1);
    }

    @Test
    void register_shouldNotEvictAliasedLoaderEvenWhenCapacityIsExceeded() {
        // Given
        InMemoryClassLoaderRegistry registry = InMemoryClassLoaderRegistry.builder().maxLoaders(1).build();
        ClassLoader loader = getClass().getClassLoader();
        registry.register("v1", loader, null);
        registry.setAlias("al/pipeline/RUN/latest", "v1");

        // When
        registry.register("v2", loader, null);

        // Then
        assertThat(registry.get("v1")).as("aliased loader is protected from automatic eviction").isSameAs(loader);
        assertThat(registry.get("v2")).as("new loader remains cached because every older loader is aliased")
                .isSameAs(loader);
        assertThat(registry.snapshotStats().cachedLoaders()).isEqualTo(2);
    }

    @Test
    void setAlias_shouldRejectMissingLoaderIds() {
        // Given
        InMemoryClassLoaderRegistry registry = InMemoryClassLoaderRegistry.builder().build();

        // When / Then
        assertThatThrownBy(() -> registry.setAlias("latest", "missing"))
                .as("aliases must not point to loaders that cannot be resolved")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void setAlias_shouldRejectTooManyProtectedLoaders() {
        // Given
        InMemoryClassLoaderRegistry registry = InMemoryClassLoaderRegistry.builder()
                .maxLoaders(10)
                .maxProtectedLoaders(1)
                .build();
        ClassLoader loader = getClass().getClassLoader();
        registry.register("v1", loader, null);
        registry.register("v2", loader, null);
        registry.setAlias("latest", "v1");

        // When / Then
        assertThatThrownBy(() -> registry.setAlias("rollback", "v2"))
                .as("operators can cap alias-protected loaders independently from the LRU cache")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot protect more than 1");
    }

    @Test
    void protectedLoaderCount_shouldExposeAliasProtectedLoaders() {
        // Given
        InMemoryClassLoaderRegistry registry = InMemoryClassLoaderRegistry.builder().maxLoaders(1).build();
        ClassLoader loader = getClass().getClassLoader();
        registry.register("v1", loader, null);
        registry.setAlias("latest", "v1");

        // When
        registry.register("v2", loader, null);

        // Then
        assertThat(registry.protectedLoaderCount()).isEqualTo(1);
        assertThat(registry.maxProtectedLoaders()).isEqualTo(InMemoryClassLoaderRegistry.DEFAULT_MAX_PROTECTED_LOADERS);
        assertThat(registry.isOverCapacityDueToProtectedLoaders()).isTrue();
    }

}
