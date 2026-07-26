package io.github.gear4jtest.external.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.github.gear4jtest.external.api.artifact.ArtifactStore;
import io.github.gear4jtest.external.api.artifact.InMemoryArtifactStore;
import io.github.gear4jtest.external.api.compiler.GeneratedSourceCompiler;
import io.github.gear4jtest.external.api.loader.ClassLoaderRegistry;
import io.github.gear4jtest.external.api.loader.GeneratedAssemblyLine;
import io.github.gear4jtest.external.api.loader.SimpleDependencyInjector;
import io.github.gear4jtest.external.api.model.OperationChainConfig;
import io.github.gear4jtest.external.api.model.OperationChainObject;
import io.github.gear4jtest.external.api.repository.OperationChainConfigRepository;
import io.github.gear4jtest.external.api.repository.OperationChainObjectRepository;
import io.github.gear4jtest.external.api.repository.OperationChainPublicationConflictException;
import io.github.gear4jtest.external.api.repository.OperationChainPublicationRepository;
import io.github.gear4jtest.external.api.repository.OperationChainPublicationStage;
import io.github.gear4jtest.external.api.repository.OperationChainRepositoryException;
import io.github.gear4jtest.external.api.repository.OperationChainTagRepository;
import io.github.gear4jtest.external.api.spi.ArtifactStoreProvider;
import io.github.gear4jtest.external.api.translator.OperationChainTranslator;
import io.github.gear4jtest.external.api.translator.OperationChainTranslatorResolver;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class AssemblyLineManagerTest {
    private final OperationChainConfigRepository configRepository = mock(OperationChainConfigRepository.class);
    private final OperationChainObjectRepository objectRepository = mock(OperationChainObjectRepository.class);
    private final OperationChainPublicationRepository publicationRepository = mock(OperationChainPublicationRepository.class);
    private final OperationChainTagRepository tagRepository = mock(OperationChainTagRepository.class);
    private final ArtifactStoreProvider storeProvider = mock(ArtifactStoreProvider.class);
    private final ClassLoaderRegistry classLoaderRegistry = mock(ClassLoaderRegistry.class);
    private final OperationChainTranslatorResolver translatorResolver = mock(OperationChainTranslatorResolver.class);
    private final GeneratedSourceCompiler compiler = mock(GeneratedSourceCompiler.class);

    @Test
    void registerAssemblyLine_shouldStoreArtifactAndPublishMetadataAtomically() throws Exception {
        // Given
        InMemoryArtifactStore artifactStore = new InMemoryArtifactStore();
        AssemblyLineManager manager = manager();
        when(configRepository.findByAssemblyLineId("line")).thenReturn(Optional.of(memoryConfig("line")));
        when(storeProvider.forConfig(any())).thenReturn(artifactStore);
        stubSuccessfulRunValidation();
        byte[] content = "<pipeline/>".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        // When
        String hash = manager.registerAssemblyLine("line", "1.0.0", ExecutionMode.TEST, content,
                                                   "application/xml", List.of("fast", "xml"), "tester");

        // Then
        assertThat(artifactStore.exists(hash)).as("the external artifact is stored before committing metadata")
                .isTrue();
        ArgumentCaptor<OperationChainObject> objectCaptor = ArgumentCaptor.forClass(OperationChainObject.class);
        verify(publicationRepository).stage(objectCaptor.capture(), eq(List.of("fast", "xml")), any());
        verify(publicationRepository).commit(any());
        assertThat(objectCaptor.getValue())
                .as("published TEST metadata points to the stored artifact hash")
                .extracting(OperationChainObject::alId, OperationChainObject::version, OperationChainObject::mode,
                            OperationChainObject::contentHash, OperationChainObject::sizeBytes,
                            OperationChainObject::mimeType, OperationChainObject::createdBy)
                .containsExactly("line", "1.0.0", ExecutionMode.TEST, hash, (long) content.length,
                                 "application/xml", "tester");
        verify(objectRepository, never()).insert(any());
        verify(tagRepository, never()).addTag(any(), any());
    }

    @Test
    void registerAssemblyLine_shouldDelegateAtomicMetadataPublicationWhenObjectRepositorySupportsIt()
            throws Exception {
        // Given
        OperationChainObjectRepository atomicObjectRepository = mock(
                                                                     OperationChainObjectRepository.class,
                                                                     withSettings()
                                                                             .extraInterfaces(OperationChainPublicationRepository.class));
        OperationChainPublicationRepository atomicPublicationRepository = (OperationChainPublicationRepository) atomicObjectRepository;
        InMemoryArtifactStore artifactStore = new InMemoryArtifactStore();
        AssemblyLineManager manager = manager(atomicObjectRepository, null);
        when(configRepository.findByAssemblyLineId("line")).thenReturn(Optional.of(memoryConfig("line")));
        when(storeProvider.forConfig(any())).thenReturn(artifactStore);
        stubSuccessfulRunValidation();
        byte[] content = "<pipeline/>".getBytes(StandardCharsets.UTF_8);

        // When
        String hash = manager.registerAssemblyLine("line", "1.0.0", ExecutionMode.TEST, content,
                                                   "application/xml", List.of("fast", "xml", "fast"), "tester");

        // Then
        ArgumentCaptor<OperationChainObject> objectCaptor = ArgumentCaptor.forClass(OperationChainObject.class);
        verify(atomicPublicationRepository).stage(objectCaptor.capture(), eq(List.of("fast", "xml")), any());
        verify(atomicPublicationRepository).commit(any());
        assertThat(objectCaptor.getValue().contentHash()).isEqualTo(hash);
        verify(atomicObjectRepository, never()).insert(any());
        verify(tagRepository, never()).addTag(any(), any());
    }

    @Test
    void registerAssemblyLine_shouldExposeConflictingRetryAsPolicyViolation() throws Exception {
        // Given
        InMemoryArtifactStore artifactStore = new InMemoryArtifactStore();
        AssemblyLineManager manager = manager(publicationRepository);
        when(configRepository.findByAssemblyLineId("line")).thenReturn(Optional.of(memoryConfig("line")));
        when(storeProvider.forConfig(any())).thenReturn(artifactStore);
        stubSuccessfulRunValidation();
        var conflict = new OperationChainPublicationConflictException(
                "Publication line:1.0.0:TEST already exists with different content or metadata");
        doThrow(conflict).when(publicationRepository).stage(any(), any(), any());

        // When / Then
        assertThatThrownBy(() -> manager.registerAssemblyLine("line", "1.0.0", ExecutionMode.TEST,
                                                              "<pipeline/>".getBytes(StandardCharsets.UTF_8),
                                                              "application/xml", List.of(), "tester"))
                .isInstanceOf(io.github.gear4jtest.external.api.exception.PolicyViolationException.class)
                .hasMessageContaining("different content or metadata")
                .hasCause(conflict);
    }

    @Test
    void registerAssemblyLine_shouldValidateTestCandidateBeforePublishingMetadata() throws Exception {
        // Given
        InMemoryArtifactStore artifactStore = new InMemoryArtifactStore();
        AssemblyLineManager manager = manager();
        when(configRepository.findByAssemblyLineId("line")).thenReturn(Optional.of(memoryConfig("line")));
        when(storeProvider.forConfig(any())).thenReturn(artifactStore);
        OperationChainTranslator translator = mock(OperationChainTranslator.class);
        when(translatorResolver.resolve("application/xml")).thenReturn(translator);
        when(translator.translate(any(byte[].class), eq("application/xml"), eq(ExecutionMode.TEST)))
                .thenReturn(new OperationChainTranslator.GenerationResult("io.test.Generated", "broken source"));
        when(compiler.compile(eq("io.test.Generated"), any(byte[].class))).thenThrow(new IllegalStateException("boom"));
        byte[] content = "<pipeline/>".getBytes(StandardCharsets.UTF_8);

        // When / Then
        assertThatThrownBy(() -> manager.registerAssemblyLine("line", "1.0.0", ExecutionMode.TEST, content,
                                                              "application/xml", List.of("fast"), "tester"))
                .isInstanceOf(io.github.gear4jtest.external.api.exception.PolicyViolationException.class)
                .hasMessageContaining("TEST publication candidate validation failed")
                .hasCauseInstanceOf(IllegalStateException.class);
        assertThat(artifactStore.exists(io.github.gear4jtest.external.api.artifact.ArtifactHashes.sha256Hex(content)))
                .as("invalid content is rejected before artifact storage")
                .isFalse();
        verify(translator).translate(any(byte[].class), eq("application/xml"), eq(ExecutionMode.TEST));
        verify(objectRepository, never()).insert(any());
        verify(tagRepository, never()).addTag(any(), any());
        verify(publicationRepository, never()).stage(any(), any(), any());
    }

    @Test
    void registerAssemblyLine_shouldNotCreateStageWhenStoreResolutionFails() throws Exception {
        // Given
        AssemblyLineManager manager = manager();
        when(configRepository.findByAssemblyLineId("line")).thenReturn(Optional.of(memoryConfig("line")));
        stubSuccessfulRunValidation();
        IllegalArgumentException resolutionFailure = new IllegalArgumentException("invalid store configuration");
        when(storeProvider.forConfig(any())).thenThrow(resolutionFailure);

        // When / Then
        assertThatThrownBy(() -> manager.registerAssemblyLine("line", "1.0.0", ExecutionMode.TEST,
                                                              "<pipeline/>".getBytes(StandardCharsets.UTF_8),
                                                              "application/xml", List.of("xml"), "tester"))
                .isSameAs(resolutionFailure);
        verify(publicationRepository, never()).stage(any(), any(), any());
        verify(publicationRepository, never()).abort(any());
        verify(publicationRepository, never()).commit(any());
    }

    @Test
    void registerAssemblyLine_shouldKeepStageWhenArtifactStorageFails() throws Exception {
        // Given
        ArtifactStore artifactStore = mock(ArtifactStore.class);
        AssemblyLineManager manager = manager();
        when(configRepository.findByAssemblyLineId("line")).thenReturn(Optional.of(memoryConfig("line")));
        when(storeProvider.forConfig(any())).thenReturn(artifactStore);
        stubSuccessfulRunValidation();
        IOException storageFailure = new IOException("store unavailable");
        when(artifactStore.put(any(byte[].class))).thenThrow(storageFailure);

        // When / Then
        assertThatThrownBy(() -> manager.registerAssemblyLine("line", "1.0.0", ExecutionMode.TEST,
                                                              "<pipeline/>".getBytes(StandardCharsets.UTF_8),
                                                              "application/xml", List.of("xml"), "tester"))
                .isSameAs(storageFailure);
        verify(publicationRepository, never()).abort(any());
        verify(publicationRepository, never()).commit(any());
    }

    @Test
    void registerAssemblyLine_shouldKeepStageWhenStoreReturnsUnexpectedHash() throws Exception {
        // Given
        ArtifactStore artifactStore = mock(ArtifactStore.class);
        AssemblyLineManager manager = manager();
        when(configRepository.findByAssemblyLineId("line")).thenReturn(Optional.of(memoryConfig("line")));
        when(storeProvider.forConfig(any())).thenReturn(artifactStore);
        stubSuccessfulRunValidation();
        when(artifactStore.put(any(byte[].class))).thenReturn("b".repeat(64));

        // When / Then
        assertThatThrownBy(() -> manager.registerAssemblyLine("line", "1.0.0", ExecutionMode.TEST,
                                                              "<pipeline/>".getBytes(StandardCharsets.UTF_8),
                                                              "application/xml", List.of("xml"), "tester"))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("returned hash")
                .hasMessageContaining("expected hash");
        verify(publicationRepository, never()).abort(any());
        verify(publicationRepository, never()).commit(any());
    }

    @Test
    void registerAssemblyLine_shouldAbortStageWhenCommitDetectsConflict() throws Exception {
        // Given
        InMemoryArtifactStore artifactStore = new InMemoryArtifactStore();
        AssemblyLineManager manager = manager();
        when(configRepository.findByAssemblyLineId("line")).thenReturn(Optional.of(memoryConfig("line")));
        when(storeProvider.forConfig(any())).thenReturn(artifactStore);
        stubSuccessfulRunValidation();
        OperationChainPublicationConflictException conflict = new OperationChainPublicationConflictException(
                "publication changed while committing");
        doThrow(conflict).when(publicationRepository).commit("stage-1.0.0-TEST");

        // When / Then
        assertThatThrownBy(() -> manager.registerAssemblyLine("line", "1.0.0", ExecutionMode.TEST,
                                                              "<pipeline/>".getBytes(StandardCharsets.UTF_8),
                                                              "application/xml", List.of("xml"), "tester"))
                .isInstanceOf(io.github.gear4jtest.external.api.exception.PolicyViolationException.class)
                .hasMessageContaining("publication changed")
                .hasCause(conflict);
        verify(publicationRepository).abort("stage-1.0.0-TEST");
    }

    @Test
    void registerAssemblyLine_shouldKeepStageWhenMetadataCommitFails() throws Exception {
        // Given
        InMemoryArtifactStore artifactStore = new InMemoryArtifactStore();
        AssemblyLineManager manager = manager();
        when(configRepository.findByAssemblyLineId("line")).thenReturn(Optional.of(memoryConfig("line")));
        when(storeProvider.forConfig(any())).thenReturn(artifactStore);
        stubSuccessfulRunValidation();
        OperationChainRepositoryException commitFailure = new OperationChainRepositoryException("database unavailable");
        doThrow(commitFailure).when(publicationRepository).commit("stage-1.0.0-TEST");
        byte[] content = "<pipeline/>".getBytes(StandardCharsets.UTF_8);

        // When / Then
        assertThatThrownBy(() -> manager.registerAssemblyLine("line", "1.0.0", ExecutionMode.TEST, content,
                                                              "application/xml", List.of("xml"), "tester"))
                .isSameAs(commitFailure);
        assertThat(artifactStore.exists(io.github.gear4jtest.external.api.artifact.ArtifactHashes.sha256Hex(content)))
                .isTrue();
        verify(publicationRepository, never()).abort(any());
    }

    @Test
    void registerAssemblyLine_shouldRejectInvalidTagsBeforeValidationOrStorage() {
        // Given
        AssemblyLineManager manager = manager();

        // When / Then
        assertThatThrownBy(() -> manager.registerAssemblyLine("line", "1.0.0", ExecutionMode.TEST,
                                                              "<pipeline/>".getBytes(StandardCharsets.UTF_8),
                                                              "application/xml", List.of(" "), "tester"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tag must not be blank");
        verify(publicationRepository, never()).stage(any(), any(), any());
        verifyNoInteractions(storeProvider, translatorResolver, compiler);
    }

    @Test
    void registerAssemblyLine_shouldRejectNullAndBlankVersions() {
        // Given
        AssemblyLineManager manager = manager();
        byte[] content = "<pipeline/>".getBytes(StandardCharsets.UTF_8);

        // When / Then
        assertThatThrownBy(() -> manager.registerAssemblyLine("line", null, ExecutionMode.TEST, content,
                                                              "application/xml", List.of(), "tester"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("version must not be blank");
        assertThatThrownBy(() -> manager.registerAssemblyLine("line", "   ", ExecutionMode.TEST, content,
                                                              "application/xml", List.of(), "tester"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("version must not be blank");
    }

    @Test
    void registerAssemblyLine_shouldAllowDirectRunAndInvalidateLatestAliasWhenConfigured() throws Exception {
        // Given
        InMemoryArtifactStore artifactStore = new InMemoryArtifactStore();
        AssemblyLineManager manager = manager();
        when(configRepository.findByAssemblyLineId("line")).thenReturn(Optional.of(memoryConfig("line", true)));
        when(storeProvider.forConfig(any())).thenReturn(artifactStore);
        stubSuccessfulRunValidation();
        byte[] content = "<pipeline/>".getBytes(StandardCharsets.UTF_8);

        // When
        String hash = manager.registerAssemblyLine("line", "1.0.0", ExecutionMode.RUN, content,
                                                   "application/xml", null, "tester");

        // Then
        ArgumentCaptor<OperationChainObject> objectCaptor = ArgumentCaptor.forClass(OperationChainObject.class);
        verify(publicationRepository).stage(objectCaptor.capture(), eq(List.of()), any());
        verify(publicationRepository).commit(any());
        assertThat(objectCaptor.getValue().contentHash()).isEqualTo(hash);
        assertThat(objectCaptor.getValue().mode()).isEqualTo(ExecutionMode.RUN);
        verify(classLoaderRegistry).clearAlias("al/line/RUN/latest");
    }

    @Test
    void registerAssemblyLine_shouldRejectDirectRunPublicationWhenConfigDoesNotAllowIt() {
        // Given
        AssemblyLineManager manager = manager();
        when(configRepository.findByAssemblyLineId("line")).thenReturn(Optional.of(memoryConfig("line")));

        // When / Then
        assertThatThrownBy(() -> manager.registerAssemblyLine("line", "1.0.0", ExecutionMode.RUN, new byte[] { 1 },
                                                              "application/xml", List.of(), "tester"))
                .isInstanceOf(io.github.gear4jtest.external.api.exception.PolicyViolationException.class)
                .hasMessageContaining("Direct RUN publication is disabled");
    }

    @Test
    void promoteTestToRun_shouldValidateCompilePublishRunObjectAndInvalidateLatestAlias() throws Exception {
        // Given
        AssemblyLineManager manager = manager();
        InMemoryArtifactStore artifactStore = new InMemoryArtifactStore();
        byte[] content = "<pipeline/>".getBytes(StandardCharsets.UTF_8);
        String contentHash = artifactStore.put(content);
        OperationChainObject testObject = object("line", "1.0.0", ExecutionMode.TEST, contentHash, content.length);
        when(configRepository.findByAssemblyLineId("line")).thenReturn(Optional.of(memoryConfig("line")));
        when(storeProvider.forConfig(any())).thenReturn(artifactStore);
        stubSuccessfulRunValidation();
        when(objectRepository.find("line", "1.0.0", ExecutionMode.TEST)).thenReturn(Optional.of(testObject));
        when(objectRepository.exists("line", "1.0.0", ExecutionMode.RUN)).thenReturn(false);

        // When
        manager.promoteTestToRun("line", "1.0.0", "promoter");

        // Then
        ArgumentCaptor<OperationChainObject> objectCaptor = ArgumentCaptor.forClass(OperationChainObject.class);
        verify(publicationRepository).stage(objectCaptor.capture(), eq(List.of()), any());
        verify(publicationRepository).commit(any());
        assertThat(objectCaptor.getValue())
                .as("promotion creates a RUN object reusing the tested artifact")
                .extracting(OperationChainObject::alId, OperationChainObject::version, OperationChainObject::mode,
                            OperationChainObject::contentHash, OperationChainObject::sizeBytes,
                            OperationChainObject::createdBy)
                .containsExactly("line", "1.0.0", ExecutionMode.RUN, testObject.contentHash(),
                                 testObject.sizeBytes(), "promoter");
        verify(classLoaderRegistry).clearAlias("al/line/RUN/latest");
    }

    @Test
    void promoteTestToRun_shouldReturnWhenMatchingRunAlreadyExists() throws Exception {
        // Given
        AssemblyLineManager manager = manager();
        OperationChainObject testObject = object("line", "1.0.0", ExecutionMode.TEST, "a".repeat(64), 42L);
        OperationChainObject runObject = object("line", "1.0.0", ExecutionMode.RUN, "a".repeat(64), 42L);
        when(objectRepository.find("line", "1.0.0", ExecutionMode.TEST)).thenReturn(Optional.of(testObject));
        when(objectRepository.exists("line", "1.0.0", ExecutionMode.RUN)).thenReturn(true);
        when(objectRepository.find("line", "1.0.0", ExecutionMode.RUN)).thenReturn(Optional.of(runObject));

        // When
        manager.promoteTestToRun("line", "1.0.0", "promoter");

        // Then
        verify(publicationRepository, never()).stage(any(), any(), any());
        verify(classLoaderRegistry, never()).clearAlias(any());
    }

    @Test
    void promoteTestToRun_shouldRejectExistingRunWithDifferentContent() {
        // Given
        AssemblyLineManager manager = manager();
        OperationChainObject testObject = object("line", "1.0.0", ExecutionMode.TEST, "a".repeat(64), 42L);
        OperationChainObject runObject = object("line", "1.0.0", ExecutionMode.RUN, "b".repeat(64), 42L);
        when(objectRepository.find("line", "1.0.0", ExecutionMode.TEST)).thenReturn(Optional.of(testObject));
        when(objectRepository.exists("line", "1.0.0", ExecutionMode.RUN)).thenReturn(true);
        when(objectRepository.find("line", "1.0.0", ExecutionMode.RUN)).thenReturn(Optional.of(runObject));

        // When / Then
        assertThatThrownBy(() -> manager.promoteTestToRun("line", "1.0.0", "promoter"))
                .isInstanceOf(io.github.gear4jtest.external.api.exception.PolicyViolationException.class)
                .hasMessageContaining("different content identity");
        verify(publicationRepository, never()).stage(any(), any(), any());
        verify(classLoaderRegistry, never()).clearAlias(any());
    }

    @Test
    void promoteTestToRun_shouldRejectExistingRunWithDifferentSize() {
        // Given
        AssemblyLineManager manager = manager();
        OperationChainObject testObject = object("line", "1.0.0", ExecutionMode.TEST, "a".repeat(64), 42L);
        OperationChainObject runObject = object("line", "1.0.0", ExecutionMode.RUN, "a".repeat(64), 43L);
        when(objectRepository.find("line", "1.0.0", ExecutionMode.TEST)).thenReturn(Optional.of(testObject));
        when(objectRepository.exists("line", "1.0.0", ExecutionMode.RUN)).thenReturn(true);
        when(objectRepository.find("line", "1.0.0", ExecutionMode.RUN)).thenReturn(Optional.of(runObject));

        // When / Then
        assertThatThrownBy(() -> manager.promoteTestToRun("line", "1.0.0", "promoter"))
                .isInstanceOf(io.github.gear4jtest.external.api.exception.PolicyViolationException.class)
                .hasMessageContaining("different content identity");
        verify(publicationRepository, never()).stage(any(), any(), any());
        verify(classLoaderRegistry, never()).clearAlias(any());
    }

    @Test
    void promoteTestToRun_shouldRejectExistingRunWithDifferentMimeType() {
        // Given
        AssemblyLineManager manager = manager();
        OperationChainObject testObject = object("line", "1.0.0", ExecutionMode.TEST, "a".repeat(64), 42L);
        OperationChainObject runObject = new OperationChainObject(null, "line", "1.0.0", ExecutionMode.RUN,
                "a".repeat(64), 42L, "application/json", Instant.parse("2026-06-16T00:00:00Z"), "tester",
                Instant.parse("2026-06-16T00:00:00Z"));
        when(objectRepository.find("line", "1.0.0", ExecutionMode.TEST)).thenReturn(Optional.of(testObject));
        when(objectRepository.exists("line", "1.0.0", ExecutionMode.RUN)).thenReturn(true);
        when(objectRepository.find("line", "1.0.0", ExecutionMode.RUN)).thenReturn(Optional.of(runObject));

        // When / Then
        assertThatThrownBy(() -> manager.promoteTestToRun("line", "1.0.0", "promoter"))
                .isInstanceOf(io.github.gear4jtest.external.api.exception.PolicyViolationException.class)
                .hasMessageContaining("different content identity");
        verify(publicationRepository, never()).stage(any(), any(), any());
        verify(classLoaderRegistry, never()).clearAlias(any());
    }

    @Test
    void promoteTestToRun_shouldRejectInvalidRunCandidateBeforePublishingRunObject() throws Exception {
        // Given
        AssemblyLineManager manager = manager();
        InMemoryArtifactStore artifactStore = new InMemoryArtifactStore();
        byte[] content = "<pipeline/>".getBytes(StandardCharsets.UTF_8);
        String contentHash = artifactStore.put(content);
        OperationChainObject testObject = object("line", "1.0.0", ExecutionMode.TEST, contentHash, content.length);
        when(configRepository.findByAssemblyLineId("line")).thenReturn(Optional.of(memoryConfig("line")));
        when(storeProvider.forConfig(any())).thenReturn(artifactStore);
        OperationChainTranslator translator = mock(OperationChainTranslator.class);
        when(translatorResolver.resolve("application/xml")).thenReturn(translator);
        when(translator.translate(any(byte[].class), eq("application/xml"), eq(ExecutionMode.RUN)))
                .thenReturn(new OperationChainTranslator.GenerationResult("io.test.Generated", "broken source"));
        when(compiler.compile(eq("io.test.Generated"), any(byte[].class))).thenThrow(new IllegalStateException("boom"));
        when(objectRepository.find("line", "1.0.0", ExecutionMode.TEST)).thenReturn(Optional.of(testObject));
        when(objectRepository.exists("line", "1.0.0", ExecutionMode.RUN)).thenReturn(false);

        // When / Then
        assertThatThrownBy(() -> manager.promoteTestToRun("line", "1.0.0", "promoter"))
                .isInstanceOf(io.github.gear4jtest.external.api.exception.PolicyViolationException.class)
                .hasMessageContaining("RUN candidate validation failed")
                .hasCauseInstanceOf(IllegalStateException.class);
        verify(translator).translate(any(byte[].class), eq("application/xml"), eq(ExecutionMode.RUN));
        verify(objectRepository, never()).insert(any());
        verify(publicationRepository, never()).stage(any(), any(), any());
    }

    @Test
    void build_shouldRejectRepositoriesWithoutAtomicPublicationCapability() {
        // When / Then
        assertThatThrownBy(() -> manager(objectRepository, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Atomic metadata publication is required")
                .hasMessageContaining("publicationRepository");
    }

    @Test
    void build_shouldRejectAtomicRepositoryWithoutStagedLifecycle() {
        // Given
        OperationChainPublicationRepository nonStagingRepository = mock(OperationChainPublicationRepository.class);

        // When / Then
        assertThatThrownBy(() -> AssemblyLineManager.builder()
                .configRepository(configRepository)
                .objectRepository(objectRepository)
                .tagRepository(tagRepository)
                .publicationRepository(nonStagingRepository)
                .storeProvider(storeProvider)
                .classLoaderRegistry(classLoaderRegistry)
                .translatorResolver(translatorResolver)
                .compiler(compiler)
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Staged metadata publication is required");
    }

    @Test
    void getOperationChain_shouldReturnBoundCachedAssemblyLineWithoutRecompiling() throws Exception {
        // Given
        AssemblyLineManager manager = manager();
        OperationChainObject runObject = object("line", "1.0.0", ExecutionMode.RUN, "b".repeat(64), 42L);
        String loaderId = AssemblyLineIdentifiers.toInternalLoaderId(runObject);
        GeneratedAssemblyLine generated = mock(GeneratedAssemblyLine.class);
        when(objectRepository.find("line", "1.0.0", ExecutionMode.RUN)).thenReturn(Optional.of(runObject));
        when(classLoaderRegistry.get(loaderId)).thenReturn(getClass().getClassLoader());
        when(classLoaderRegistry.getBoundAssemblyLine(loaderId)).thenReturn(generated);

        // When
        GeneratedAssemblyLine result = manager.getOperationChain("line", "1.0.0", ExecutionMode.RUN);

        // Then
        assertThat(result).as("cached generated assembly line is returned directly").isSameAs(generated);
        verify(classLoaderRegistry, never()).setAlias(any(), any());
    }

    @Test
    void getOperationChainLatestRun_shouldClearStaleAliasAndBindCurrentRun() throws Exception {
        // Given
        AssemblyLineManager manager = manager();
        OperationChainObject latestRun = object("line", "2.0.0", ExecutionMode.RUN, "c".repeat(64), 42L);
        String loaderId = AssemblyLineIdentifiers.toInternalLoaderId(latestRun);
        GeneratedAssemblyLine generated = mock(GeneratedAssemblyLine.class);
        when(objectRepository.findLatestRun("line")).thenReturn(Optional.of(latestRun));
        when(classLoaderRegistry.resolveAlias("al/line/RUN/latest")).thenReturn("stale-loader-id");
        when(classLoaderRegistry.get(loaderId)).thenReturn(getClass().getClassLoader());
        when(classLoaderRegistry.getBoundAssemblyLine(loaderId)).thenReturn(generated);

        // When
        GeneratedAssemblyLine result = manager.getOperationChain("line", ExecutionMode.RUN);

        // Then
        assertThat(result).as("latest RUN resolves to the current persisted object").isSameAs(generated);
        verify(classLoaderRegistry).clearAlias("al/line/RUN/latest");
        verify(classLoaderRegistry).setAlias("al/line/RUN/latest", loaderId);
    }

    private AssemblyLineManager manager() {
        return manager(objectRepository, publicationRepository);
    }

    private AssemblyLineManager manager(OperationChainPublicationRepository atomicPublicationRepository) {
        return manager(objectRepository, atomicPublicationRepository);
    }

    private AssemblyLineManager manager(OperationChainObjectRepository operationChainObjectRepository,
                                        OperationChainPublicationRepository atomicPublicationRepository) {
        OperationChainPublicationRepository effectivePublicationRepository = atomicPublicationRepository;
        if (effectivePublicationRepository == null
                && operationChainObjectRepository instanceof OperationChainPublicationRepository detected) {
            effectivePublicationRepository = detected;
        }
        if (effectivePublicationRepository != null) {
            when(effectivePublicationRepository.supportsStaging()).thenReturn(true);
            when(effectivePublicationRepository.stage(any(), any(), any())).thenAnswer(invocation -> {
                OperationChainObject object = invocation.getArgument(0);
                List<String> tags = invocation.getArgument(1);
                return new OperationChainPublicationStage("stage-" + object.version() + "-" + object.mode(), object,
                        tags, Instant.parse("2026-07-16T00:00:00Z"));
            });
        }
        return AssemblyLineManager.builder()
                .configRepository(configRepository)
                .objectRepository(operationChainObjectRepository)
                .tagRepository(tagRepository)
                .publicationRepository(atomicPublicationRepository)
                .storeProvider(storeProvider)
                .classLoaderRegistry(classLoaderRegistry)
                .translatorResolver(translatorResolver)
                .compiler(compiler)
                .dependencyInjector(new SimpleDependencyInjector())
                .generatedClassParent(getClass().getClassLoader())
                .build();
    }

    private void stubSuccessfulRunValidation() throws Exception {
        OperationChainTranslator translator = mock(OperationChainTranslator.class);
        when(translatorResolver.resolve("application/xml")).thenReturn(translator);
        when(translator.translate(any(byte[].class), eq("application/xml"), any(ExecutionMode.class)))
                .thenReturn(new OperationChainTranslator.GenerationResult("io.test.Generated",
                        "package io.test; public final class Generated {}"));
        when(compiler.compile(eq("io.test.Generated"), any(byte[].class)))
                .thenReturn(Map.of("io.test.Generated", new byte[] { 1 }));
    }

    private static OperationChainConfig memoryConfig(String alId) {
        return memoryConfig(alId, false);
    }

    private static OperationChainConfig memoryConfig(String alId, boolean allowRunPublicationWithoutTest) {
        return new OperationChainConfig(alId, allowRunPublicationWithoutTest, StoreType.MEMORY, Map.of());
    }

    private static OperationChainObject object(String alId,
                                               String version,
                                               ExecutionMode mode,
                                               String contentHash,
                                               long sizeBytes) {
        return new OperationChainObject(null, alId, version, mode, contentHash, sizeBytes, "application/xml",
                Instant.parse("2026-06-16T00:00:00Z"), "tester", Instant.parse("2026-06-16T00:00:00Z"));
    }
}
