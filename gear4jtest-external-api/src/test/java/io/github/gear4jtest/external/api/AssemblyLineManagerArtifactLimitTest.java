package io.github.gear4jtest.external.api;

import java.io.IOException;

import io.github.gear4jtest.external.api.loader.ClassLoaderRegistry;
import io.github.gear4jtest.external.api.repository.OperationChainConfigRepository;
import io.github.gear4jtest.external.api.repository.OperationChainObjectRepository;
import io.github.gear4jtest.external.api.repository.OperationChainPublicationRepository;
import io.github.gear4jtest.external.api.repository.OperationChainTagRepository;
import io.github.gear4jtest.external.api.storage.ArtifactStoreProvider;
import io.github.gear4jtest.external.api.translator.OperationChainTranslatorResolver;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AssemblyLineManagerArtifactLimitTest {
    @Test
    void registerAssemblyLine_shouldRejectArtifactsAboveDefaultLimitBeforeStoreResolution() {
        // Given
        OperationChainConfigRepository configRepository = mock(OperationChainConfigRepository.class);
        OperationChainObjectRepository objectRepository = mock(OperationChainObjectRepository.class);
        OperationChainTagRepository tagRepository = mock(OperationChainTagRepository.class);
        OperationChainPublicationRepository publicationRepository = mock(OperationChainPublicationRepository.class);
        ArtifactStoreProvider storeProvider = mock(ArtifactStoreProvider.class);
        ClassLoaderRegistry classLoaderRegistry = mock(ClassLoaderRegistry.class);
        OperationChainTranslatorResolver translatorResolver = mock(OperationChainTranslatorResolver.class);
        AssemblyLineManager manager = AssemblyLineManager.builder()
                .configRepository(configRepository)
                .objectRepository(objectRepository)
                .tagRepository(tagRepository)
                .publicationRepository(publicationRepository)
                .storeProvider(storeProvider)
                .classLoaderRegistry(classLoaderRegistry)
                .translatorResolver(translatorResolver)
                .build();
        byte[] tooLargeContent = new byte[(int) AssemblyLineManager.DEFAULT_MAX_ARTIFACT_SIZE_BYTES + 1];

        // When / Then
        assertThatThrownBy(() -> manager.registerAssemblyLine("line", "1.0.0", ExecutionMode.TEST, tooLargeContent,
                                                              "application/xml", null, "tester"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("exceeds configured maxArtifactSizeBytes=")
                .hasMessageContaining(String.valueOf(AssemblyLineManager.DEFAULT_MAX_ARTIFACT_SIZE_BYTES));
        verifyNoInteractions(configRepository, objectRepository, tagRepository, publicationRepository, storeProvider,
                             classLoaderRegistry, translatorResolver);
    }
}
