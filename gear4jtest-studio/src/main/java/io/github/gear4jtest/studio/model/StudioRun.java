package io.github.gear4jtest.studio.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Durable observation journal; its identity is independent of the receipt and
 * the engine run.
 */
public record StudioRun(UUID id,
                        UUID resourceId,
                        UUID chainId,
                        Origin origin,
                        Provenance provenance,
                        String status,
                        Instant createdAt,
                        Instant updatedAt,
                        Instant startedAt,
                        Instant endedAt,
                        long sequence,
                        RunObservation observation,
                        Instant staleAt) {
    public StudioRun(UUID id,
                     UUID resourceId,
                     UUID chainId,
                     Origin origin,
                     Provenance provenance,
                     String status,
                     Instant createdAt,
                     Instant updatedAt,
                     Instant startedAt,
                     Instant endedAt,
                     long sequence,
                     RunObservation observation) {
        this(id, resourceId, chainId, origin, provenance, status, createdAt, updatedAt, startedAt, endedAt, sequence,
                observation, null);
    }

    public enum Origin {
        CONTROLLED_TEST, HOST_RUN
    }

    public record Provenance(String runtimeContextId,
                             String runtimeAssemblyLineId,
                             String applicationBuild,
                             String catalogFingerprint,
                             String artifactHash,
                             String actor,
                             String mode,
                             UUID requestId,
                             UUID draftId,
                             UUID revisionId,
                             long revision,
                             UUID versionId,
                             String versionLabel,
                             ScenarioRef scenario,
                             ResolvedFixture fixture) {
        public Provenance(String runtimeContextId,
                          String runtimeAssemblyLineId,
                          String applicationBuild,
                          String catalogFingerprint,
                          String artifactHash,
                          String actor,
                          String mode,
                          UUID requestId,
                          UUID draftId,
                          UUID revisionId,
                          long revision,
                          UUID versionId,
                          String versionLabel) {
            this(runtimeContextId, runtimeAssemblyLineId, applicationBuild, catalogFingerprint, artifactHash,
                    actor, mode, requestId, draftId, revisionId, revision, versionId, versionLabel, null, null);
        }

        public Provenance withArtifactHash(String hash) {
            return new Provenance(runtimeContextId, runtimeAssemblyLineId, applicationBuild, catalogFingerprint,
                    hash, actor, mode, requestId, draftId, revisionId, revision, versionId, versionLabel, scenario,
                    fixture);
        }

        public Provenance withScenario(ScenarioRef reference) {
            return new Provenance(runtimeContextId, runtimeAssemblyLineId, applicationBuild, catalogFingerprint,
                    artifactHash, actor, mode, requestId, draftId, revisionId, revision, versionId, versionLabel,
                    reference, fixture);
        }

        public Provenance withFixture(ResolvedFixture resolved) {
            return new Provenance(runtimeContextId, runtimeAssemblyLineId, applicationBuild, catalogFingerprint,
                    artifactHash, actor, mode, requestId, draftId, revisionId, revision, versionId, versionLabel,
                    scenario, resolved);
        }
    }

    public StudioRun summary() {
        return new StudioRun(id, resourceId, chainId, origin, provenance, status, createdAt, updatedAt,
                startedAt, endedAt, sequence, null, staleAt);
    }

    public StudioRun withoutFixtureProvenance() {
        return new StudioRun(id, resourceId, chainId, origin, provenance.withFixture(null), status, createdAt,
                updatedAt, startedAt, endedAt, sequence, observation, staleAt);
    }

    public Long durationMillis() {
        return startedAt == null || endedAt == null ? null
                : Math.max(0, java.time.Duration.between(startedAt, endedAt).toMillis());
    }
}
