package io.github.gear4jtest.external.api;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

import io.github.gear4jtest.external.api.artifact.Artifact;
import io.github.gear4jtest.external.api.artifact.ArtifactHashes;
import io.github.gear4jtest.external.api.artifact.ArtifactStore;
import io.github.gear4jtest.external.api.compiler.GeneratedSourceCompiler;
import io.github.gear4jtest.external.api.exception.PolicyViolationException;
import io.github.gear4jtest.external.api.model.OperationChainObject;
import io.github.gear4jtest.external.api.translator.OperationChainTranslator;
import io.github.gear4jtest.external.api.translator.OperationChainTranslatorResolver;

import static java.util.Objects.requireNonNull;

final class AssemblyLinePublicationValidator {
    private final OperationChainTranslatorResolver translatorResolver;
    private final GeneratedSourceCompiler compiler;
    private final long maxArtifactSizeBytes;

    AssemblyLinePublicationValidator(OperationChainTranslatorResolver translatorResolver,
                                     GeneratedSourceCompiler compiler,
                                     long maxArtifactSizeBytes) {
        this.translatorResolver = requireNonNull(translatorResolver);
        this.compiler = requireNonNull(compiler);
        this.maxArtifactSizeBytes = AssemblyLineIdentifiers.requireValidArtifactSize(maxArtifactSizeBytes);
    }

    void validatePublicationCandidate(String alId, OperationChainObject object, byte[] content)
            throws PolicyViolationException {
        Objects.requireNonNull(content, "content must not be null");
        validateContentIdentity(content, object, object.mode() + " publication candidate");
        validateBytes(alId, object, content, object.mode() + " publication candidate");
    }

    void validateRunCandidate(String alId, OperationChainObject object, ArtifactStore store)
            throws PolicyViolationException {
        byte[] content = readArtifact(alId, object, store);
        validateContentIdentity(content, object, "RUN candidate");
        validateBytes(alId, object, content, "RUN candidate");
    }

    private static void validateContentIdentity(byte[] content,
                                                OperationChainObject object,
                                                String candidateLabel)
            throws PolicyViolationException {
        try {
            ArtifactHashes.requireContentIdentity(content, object.contentHash(), object.sizeBytes(), candidateLabel);
        } catch (IOException exception) {
            throw new PolicyViolationException(candidateLabel + " content does not match contentHash="
                    + object.contentHash(), exception);
        }
    }

    private void validateBytes(String alId,
                               OperationChainObject object,
                               byte[] bytes,
                               String candidateLabel)
            throws PolicyViolationException {
        String mediaType = AssemblyLineIdentifiers.normalizeMediaType(object.mimeType());
        try {
            AssemblyLineIdentifiers.requireAllowedArtifactSize(bytes.length, maxArtifactSizeBytes,
                                                               "Assembly line artifact");
            OperationChainTranslator.GenerationResult translated = translate(bytes, mediaType, object.mode());
            Map<String, byte[]> compiled = compiler.compile(translated.className(),
                                                            translated.formattedSource()
                                                                    .getBytes(StandardCharsets.UTF_8));
            if (compiled == null || !compiled.containsKey(translated.className())) {
                throw new PolicyViolationException(("%s validation failed for alId=%s, "
                        + "version=%s: compiler did not produce the generated class %s")
                        .formatted(candidateLabel, alId, object.version(), translated.className()));
            }
        } catch (PolicyViolationException e) {
            throw e;
        } catch (Exception e) {
            throw new PolicyViolationException(("%s validation failed for alId=%s, "
                    + "version=%s, mediaType=%s").formatted(candidateLabel, alId, object.version(), mediaType), e);
        }
    }

    private OperationChainTranslator.GenerationResult translate(byte[] bytes,
                                                                String mediaType,
                                                                ExecutionMode mode)
            throws Exception {
        OperationChainTranslator translator = translatorResolver.resolve(mediaType);
        return translator.translate(bytes, mediaType, mode);
    }

    private byte[] readArtifact(String alId, OperationChainObject object, ArtifactStore store)
            throws PolicyViolationException {
        try {
            Artifact artifact = requireNonNull(store, "store must not be null").get(object.contentHash())
                    .orElseThrow(() -> new IOException("Artifact not found for hash=" + object.contentHash()));
            ArtifactHashes.requireSha256Match(object.contentHash(), artifact.hashHex(),
                                              "RUN candidate artifact metadata");
            if (artifact.size() != object.sizeBytes()) {
                throw new IOException("RUN candidate artifact metadata size mismatch: expected "
                        + object.sizeBytes() + " but found " + artifact.size());
            }
            AssemblyLineIdentifiers.requireAllowedArtifactSize(artifact.size(), maxArtifactSizeBytes,
                                                               "Assembly line artifact " + object.contentHash());
            try (InputStream in = artifact.openStreamChecked()) {
                return ArtifactStore.readAllBytes(in, maxArtifactSizeBytes);
            }
        } catch (Exception exception) {
            throw new PolicyViolationException(
                    "RUN candidate artifact could not be read for alId=" + alId + ", version=" + object.version(),
                    exception);
        }
    }
}
