package io.github.gear4jtest.studio.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable publication candidate. Source bytes are UTF-8 and pinned forever.
 */
public record StudioVersion(UUID id,
                            UUID chainId,
                            UUID draftId,
                            UUID revisionId,
                            long revision,
                            String runtimeAssemblyLineId,
                            String label,
                            String author,
                            Instant createdAt,
                            String runtimeContextId,
                            String applicationBuild,
                            String catalogFingerprint,
                            String mediaType,
                            String artifactHash,
                            String source) {
    @Override
    public String toString() {
        return "StudioVersion[id=" + id + ", chainId=" + chainId + ", label=" + label + ", hash=" + artifactHash + "]";
    }
}
