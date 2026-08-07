package io.github.gear4jtest.external.api;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.gear4jtest.external.api.artifact.InMemoryArtifactStore;
import io.github.gear4jtest.external.api.compiler.GeneratedSourceCompiler;
import io.github.gear4jtest.external.api.compiler.JavaxToolsGeneratedSourceCompiler;
import io.github.gear4jtest.external.api.loader.InMemoryClassLoaderRegistry;
import io.github.gear4jtest.external.api.model.OperationChainConfig;
import io.github.gear4jtest.external.api.repository.InMemoryOperationChainRepository;
import io.github.gear4jtest.external.api.repository.OperationChainConfigRepository;
import io.github.gear4jtest.external.api.translator.OperationChainTranslator;
import io.github.gear4jtest.external.api.translator.OperationChainTranslatorResolver;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AssemblyLineManagerCompilationCacheTest {
    private static final String GENERATED_CLASS = "io.github.gear4jtest.generated.CachedGenerated";
    private static final String GENERATED_SOURCE = """
            package io.github.gear4jtest.generated;

            public final class CachedGenerated
                    implements io.github.gear4jtest.external.api.loader.GeneratedAssemblyLine {
                @Override
                public io.github.gear4jtest.core.api.AssemblyLine getAssemblyLineDefinition() {
                    return null;
                }
            }
            """;

    @Test
    void publicationValidationAndFirstRuntimeLoad_shouldShareCompiledBytecode() throws Exception {
        // Given
        OperationChainConfigRepository configRepository = mock(OperationChainConfigRepository.class);
        when(configRepository.findByAssemblyLineId("line"))
                .thenReturn(Optional.of(new OperationChainConfig("line", false, StoreType.MEMORY, Map.of())));
        InMemoryOperationChainRepository metadata = new InMemoryOperationChainRepository();
        InMemoryArtifactStore artifactStore = new InMemoryArtifactStore();
        OperationChainTranslator translator = new OperationChainTranslator() {
            @Override
            public boolean supports(String mediaType) {
                return "application/xml".equals(mediaType);
            }

            @Override
            public GenerationResult translate(byte[] content, String mediaType) {
                return new GenerationResult(GENERATED_CLASS, GENERATED_SOURCE);
            }
        };
        AtomicInteger compilations = new AtomicInteger();
        GeneratedSourceCompiler javac = new JavaxToolsGeneratedSourceCompiler(getClass().getClassLoader());
        GeneratedSourceCompiler countingCompiler = (className, sourceCode) -> {
            compilations.incrementAndGet();
            return javac.compile(className, sourceCode);
        };
        try (AssemblyLineManager manager = AssemblyLineManager.builder()
                .configRepository(configRepository)
                .objectRepository(metadata)
                .tagRepository(metadata)
                .publicationRepository(metadata)
                .storeProvider(config -> artifactStore)
                .classLoaderRegistry(InMemoryClassLoaderRegistry.builder().build())
                .translatorResolver(new OperationChainTranslatorResolver(List.of(translator)))
                .compiler(countingCompiler)
                .generatedClassParent(getClass().getClassLoader())
                .build()) {
            byte[] content = "<pipeline/>".getBytes(StandardCharsets.UTF_8);

            // When
            manager.registerAssemblyLine("line", "1.0.0", ExecutionMode.TEST, content,
                                         "application/xml", List.of(), "tester");
            var loaded = manager.getOperationChain("line", "1.0.0", ExecutionMode.TEST);

            // Then
            assertThat(loaded).isNotNull();
            assertThat(compilations).hasValue(1);
            assertThat(manager.compilationStats())
                    .extracting(GeneratedCompilationStats::cacheHits,
                                GeneratedCompilationStats::cacheMisses,
                                GeneratedCompilationStats::successfulCompilations)
                    .containsExactly(1L, 1L, 1L);
            assertThat(manager.loadingStats())
                    .extracting(GeneratedLoadingStats::successfulLoads,
                                GeneratedLoadingStats::timedOutLoads,
                                GeneratedLoadingStats::rejectedLoads)
                    .containsExactly(1L, 0L, 0L);
            assertThat(manager.loadingStats().artifactReadDurationNanos()).isPositive();
            assertThat(manager.loadingStats().translationDurationNanos()).isPositive();
            assertThat(manager.loadingStats().compilationDurationNanos()).isPositive();
            assertThat(manager.loadingStats().instantiationDurationNanos()).isPositive();
        }
    }
}
