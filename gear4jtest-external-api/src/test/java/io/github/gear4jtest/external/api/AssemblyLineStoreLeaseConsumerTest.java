package io.github.gear4jtest.external.api;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import io.github.gear4jtest.external.api.artifact.ArtifactStore;
import io.github.gear4jtest.external.api.compiler.JavaxToolsGeneratedSourceCompiler;
import io.github.gear4jtest.external.api.loader.InMemoryClassLoaderRegistry;
import io.github.gear4jtest.external.api.loader.SimpleDependencyInjector;
import io.github.gear4jtest.external.api.model.OperationChainObject;
import io.github.gear4jtest.external.api.repository.OperationChainObjectRepository;
import io.github.gear4jtest.external.api.repository.OperationChainPublicationRepository;
import io.github.gear4jtest.external.api.repository.OperationChainPublicationStage;
import io.github.gear4jtest.external.api.translator.OperationChainTranslator;
import io.github.gear4jtest.external.api.translator.OperationChainTranslatorResolver;
import org.junit.jupiter.api.Test;

import static io.github.gear4jtest.external.api.AssemblyLineStoreLeaseTest.TrackingStore;
import static io.github.gear4jtest.external.api.AssemblyLineStoreLeaseTest.configurations;
import static io.github.gear4jtest.external.api.AssemblyLineStoreLeaseTest.resolver;
import static io.github.gear4jtest.external.api.GeneratedAssemblyLineLoaderTestFixture.GENERATED_CLASS;
import static io.github.gear4jtest.external.api.GeneratedAssemblyLineLoaderTestFixture.GENERATED_SOURCE;
import static io.github.gear4jtest.external.api.GeneratedAssemblyLineLoaderTestFixture.object;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssemblyLineStoreLeaseConsumerTest {
    @Test
    void register_shouldKeepTheStoreAliveThroughStageUploadAndCommit() throws Exception {
        publicationDuringEviction(false);
    }

    @Test
    void register_shouldReleaseTheBorrowAfterStagingFails() throws Exception {
        publicationDuringEviction(true);
    }

    private void publicationDuringEviction(boolean failStaging) throws Exception {
        // Given
        TrackingStore store = new TrackingStore();
        IllegalStateException stageFailure = new IllegalStateException("stage failed");
        OperationChainPublicationRepository publications = mock(OperationChainPublicationRepository.class);
        try (AssemblyLineStoreResolver resolver = resolver(store, new TrackingStore())) {
            when(publications.stage(any(OperationChainObject.class), anyList(), anyString())).thenAnswer(call -> {
                evictFirst(resolver);
                assertThat(store.closes).hasValue(0);
                if (failStaging) {
                    throw stageFailure;
                }
                return new OperationChainPublicationStage("stage", call.getArgument(0), call.getArgument(1),
                        call.getArgument(2), Instant.now());
            });
            doAnswer(call -> {
                assertThat(store.closes).hasValue(0);
                return null;
            }).when(publications).commit("stage");
            AssemblyLinePublicationService service = new AssemblyLinePublicationService(configurations(),
                    mock(OperationChainObjectRepository.class), publications, resolver,
                    mock(AssemblyLineAliasService.class), mock(AssemblyLinePublicationValidator.class),
                    ArtifactStore.DEFAULT_MAX_ARTIFACT_SIZE_BYTES);

            // When / Then
            if (failStaging) {
                assertThatThrownBy(() -> service.registerAssemblyLine("first", "1.0.0", ExecutionMode.TEST,
                                                                      store.bytes, "application/xml", List.of(),
                                                                      "tester"))
                        .isSameAs(stageFailure);
                verify(publications, never()).commit(anyString());
            } else {
                assertThat(service.registerAssemblyLine("first", "1.0.0", ExecutionMode.TEST,
                                                        store.bytes, "application/xml", List.of(), "tester"))
                        .isEqualTo(store.hash);
                verify(publications).commit("stage");
            }
            assertThat(store.closes).hasValue(1);
        }
    }

    @Test
    void promote_shouldRetainTheStoreThroughValidationAndCommit() throws Exception {
        // Given
        TrackingStore store = new TrackingStore();
        OperationChainObjectRepository objects = mock(OperationChainObjectRepository.class);
        when(objects.find("first", "1.0.0", ExecutionMode.TEST))
                .thenReturn(Optional.of(object("first", "1.0.0", store.hash, store.bytes.length)));
        OperationChainPublicationRepository publications = mock(OperationChainPublicationRepository.class);
        AssemblyLinePublicationValidator validator = mock(AssemblyLinePublicationValidator.class);
        try (AssemblyLineStoreResolver resolver = resolver(store, new TrackingStore())) {
            doAnswer(call -> {
                evictFirst(resolver);
                assertThat(store.exists(store.hash)).isTrue();
                return null;
            }).when(validator).validateRunCandidate(eq("first"), any(OperationChainObject.class), eq(store));
            when(publications.stage(any(OperationChainObject.class), anyList(), anyString()))
                    .thenAnswer(call -> new OperationChainPublicationStage("stage", call.getArgument(0),
                            call.getArgument(1),
                            call.getArgument(2), Instant.now()));
            doAnswer(call -> {
                assertThat(store.closes).hasValue(0);
                return null;
            }).when(publications).commit("stage");
            AssemblyLinePublicationService service = new AssemblyLinePublicationService(configurations(), objects,
                    publications, resolver, mock(AssemblyLineAliasService.class), validator,
                    ArtifactStore.DEFAULT_MAX_ARTIFACT_SIZE_BYTES);

            // When
            service.promoteTestToRun("first", "1.0.0", "tester");

            // Then
            verify(publications).commit("stage");
            assertThat(store.closes).hasValue(1);
        }
    }

    @Test
    void load_shouldCloseTheStreamBeforeTheEvictedStoreAfterSuccessfulReading() throws Exception {
        loadingDuringEviction(false);
    }

    @Test
    void load_shouldCloseTheStreamAndEvictedStoreAfterReadingFails() throws Exception {
        loadingDuringEviction(true);
    }

    private void loadingDuringEviction(boolean failReading) throws Exception {
        // Given
        TrackingStore store = new TrackingStore();
        if (failReading) {
            store.readFailure = new IOException("read failed");
        }
        OperationChainTranslator translator = mock(OperationChainTranslator.class);
        when(translator.translate(any(byte[].class), eq("application/xml"), eq(ExecutionMode.TEST)))
                .thenReturn(new OperationChainTranslator.GenerationResult(GENERATED_CLASS, GENERATED_SOURCE));
        OperationChainTranslatorResolver translators = mock(OperationChainTranslatorResolver.class);
        when(translators.resolve("application/xml")).thenReturn(translator);
        try (AssemblyLineStoreResolver resolver = resolver(store, new TrackingStore());
                GeneratedAssemblyLineLoader loader = new GeneratedAssemblyLineLoader(resolver,
                        InMemoryClassLoaderRegistry.builder().build(), translators,
                        new JavaxToolsGeneratedSourceCompiler(getClass().getClassLoader()),
                        new SimpleDependencyInjector(),
                        getClass().getClassLoader(), ArtifactStore.DEFAULT_MAX_ARTIFACT_SIZE_BYTES)) {
            store.onRead = () -> evictFirst(resolver);
            OperationChainObject object = object("first", "1.0.0", store.hash, store.bytes.length);

            // When / Then
            if (failReading) {
                assertThatThrownBy(() -> loader.loadOrCompile("first", object)).isSameAs(store.readFailure);
            } else {
                assertThat(loader.loadOrCompile("first", object)).isNotNull();
            }
            assertThat(store.closes).hasValue(1);
            assertThat(store.streamCloses).hasValue(1);
            assertThat(store.lifecycle).containsExactly("stream closed", "store closed");
        }
    }

    private static void evictFirst(AssemblyLineStoreResolver resolver) {
        try (var lease = resolver.acquire("second")) {
            assertThat(lease.store()).isNotNull();
        }
    }
}
