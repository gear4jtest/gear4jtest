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
                            String source,
                            int definitionSchemaVersion,
                            String formatAdapterId,
                            String formatAdapterVersion) {
    public StudioVersion(UUID id,
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
        this(id, chainId, draftId, revisionId, revision, runtimeAssemblyLineId, label, author, createdAt,
                runtimeContextId, applicationBuild, catalogFingerprint, mediaType, artifactHash, source,
                0, "unknown", "unknown");
    }

    @Override
    public String toString() {
        return "StudioVersion[id=" + id + ", chainId=" + chainId + ", label=" + label + ", hash=" + artifactHash + "]";
    }
}
