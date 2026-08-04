package io.github.gear4jtest.external.api;

import java.io.IOException;

import io.github.gear4jtest.external.api.artifact.ArtifactStore;
import io.github.gear4jtest.external.api.identity.OperationChainIdentityCodec;
import io.github.gear4jtest.external.api.model.OperationChainObject;

import static java.util.Objects.requireNonNull;

final class AssemblyLineIdentifiers {
    private AssemblyLineIdentifiers() {
    }

    static String normalizeMediaType(String mediaType) {
        return (mediaType == null || mediaType.isBlank()) ? "application/xml" : mediaType;
    }

    static long requireValidArtifactSize(long maxArtifactSizeBytes) {
        if (maxArtifactSizeBytes < ArtifactStore.UNLIMITED_SIZE) {
            throw new IllegalArgumentException("maxArtifactSizeBytes must be -1 or >= 0");
        }
        return maxArtifactSizeBytes;
    }

    static void requireAllowedArtifactSize(long sizeBytes, long maxArtifactSizeBytes, String description)
            throws IOException {
        if (maxArtifactSizeBytes >= 0 && sizeBytes > maxArtifactSizeBytes) {
            throw new IOException(description + " exceeds configured maxArtifactSizeBytes=" + maxArtifactSizeBytes
                    + ". actualSizeBytes=" + sizeBytes);
        }
    }

    static String toInternalLoaderId(OperationChainObject obj) {
        return OperationChainIdentityCodec.loaderId(obj);
    }

    static String latestAlias(String alId) {
        return "al/" + requireNonNull(alId) + "/RUN/latest";
    }
}
