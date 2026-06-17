package io.github.gear4jtest.external.api;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import io.github.gear4jtest.external.api.artifact.Artifact;
import io.github.gear4jtest.external.api.artifact.ArtifactStore;
import io.github.gear4jtest.external.api.compiler.GeneratedSourceCompiler;
import io.github.gear4jtest.external.api.model.OperationChainObject;
import io.github.gear4jtest.external.api.translator.OperationChainTranslator;
import io.github.gear4jtest.external.api.translator.OperationChainTranslatorResolver;

import static java.util.Objects.requireNonNull;

final class AssemblyLinePublicationValidator {
    private final AssemblyLineStoreResolver storeResolver;
    private final OperationChainTranslatorResolver translatorResolver;
    private final GeneratedSourceCompiler compiler;
    private final long maxArtifactSizeBytes;

    AssemblyLinePublicationValidator(AssemblyLineStoreResolver storeResolver,
                                     OperationChainTranslatorResolver translatorResolver,
                                     GeneratedSourceCompiler compiler,
                                     long maxArtifactSizeBytes) {
        this.storeResolver = requireNonNull(storeResolver);
        this.translatorResolver = requireNonNull(translatorResolver);
        this.compiler = requireNonNull(compiler);
        this.maxArtifactSizeBytes = AssemblyLineIdentifiers.requireValidArtifactSize(maxArtifactSizeBytes);
    }

    void validateRunCandidate(String alId, OperationChainObject object)
            throws AssemblyLineManager.PolicyViolationException {
        String mediaType = AssemblyLineIdentifiers.normalizeMediaType(object.mimeType());
        try {
            byte[] bytes = readArtifact(alId, object);
            OperationChainTranslator.GenerationResult translated = translate(bytes, mediaType);
            Map<String, byte[]> compiled = compiler.compile(translated.className(),
                                                            translated.formattedSource()
                                                                    .getBytes(StandardCharsets.UTF_8));
            if (compiled == null || !compiled.containsKey(translated.className())) {
                throw new AssemblyLineManager.PolicyViolationException(("RUN candidate validation failed for alId=%s, "
                        + "version=%s: compiler did not produce the generated class %s")
                                .formatted(alId, object.version(), translated.className()));
            }
        } catch (AssemblyLineManager.PolicyViolationException e) {
            throw e;
        } catch (Exception e) {
            throw new AssemblyLineManager.PolicyViolationException(("RUN candidate validation failed for alId=%s, "
                    + "version=%s, mediaType=%s").formatted(alId, object.version(), mediaType), e);
        }
    }

    private OperationChainTranslator.GenerationResult translate(byte[] bytes, String mediaType) throws Exception {
        OperationChainTranslator translator = translatorResolver.resolve(mediaType);
        return translator.translate(bytes, mediaType);
    }

    private byte[] readArtifact(String alId, OperationChainObject object) throws IOException {
        ArtifactStore store = storeResolver.resolve(alId);
        Artifact artifact = store.get(object.contentHash())
                .orElseThrow(() -> new IOException("Artifact not found for hash=" + object.contentHash()));
        AssemblyLineIdentifiers.requireAllowedArtifactSize(artifact.size(), maxArtifactSizeBytes,
                                                           "Assembly line artifact " + object.contentHash());
        try (InputStream in = artifact.openStream()) {
            return ArtifactStore.readAllBytes(in, maxArtifactSizeBytes);
        }
    }
}
