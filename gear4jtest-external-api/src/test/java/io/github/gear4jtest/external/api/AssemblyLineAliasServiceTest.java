package io.github.gear4jtest.external.api;

import io.github.gear4jtest.external.api.loader.GeneratedAssemblyLine;
import io.github.gear4jtest.external.api.loader.InMemoryClassLoaderRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AssemblyLineAliasServiceTest {
    @Test
    void completeLatestResolution_shouldNotRestoreAliasInvalidatedDuringCompilation() {
        // Given
        InMemoryClassLoaderRegistry registry = InMemoryClassLoaderRegistry.builder().build();
        String oldLoaderId = "line:1.0.0:RUN:old";
        String newLoaderId = "line:2.0.0:RUN:new";
        registry.register(oldLoaderId, getClass().getClassLoader(), mock(GeneratedAssemblyLine.class));
        registry.register(newLoaderId, getClass().getClassLoader(), mock(GeneratedAssemblyLine.class));
        AssemblyLineAliasService aliasService = new AssemblyLineAliasService(registry);
        var oldResolution = aliasService.beginLatestResolution("line", oldLoaderId);

        // When
        aliasService.invalidateLatestRun("line");
        var newResolution = aliasService.beginLatestResolution("line", newLoaderId);
        aliasService.completeLatestResolution(newResolution);
        aliasService.completeLatestResolution(oldResolution);

        // Then
        assertThat(aliasService.resolveLatestRunLoaderId("line"))
                .as("a stale compilation must not overwrite the alias installed after invalidation")
                .isEqualTo(newLoaderId);
    }
}
