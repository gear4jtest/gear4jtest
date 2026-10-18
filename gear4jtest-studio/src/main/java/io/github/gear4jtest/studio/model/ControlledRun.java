package io.github.gear4jtest.studio.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Management provenance is separate from the engine's transient runtime trace.
 */
public record ControlledRun(UUID requestId,
                            UUID runId,
                            UUID draftId,
                            UUID revisionId,
                            long revision,
                            String actor,
                            String runtimeContext,
                            String applicationBuild,
                            String mode,
                            String catalogFingerprint,
                            String artifactHash,
                            String outcome,
                            Instant startedAt,
                            Instant endedAt,
                            CapturedValue input,
                            CapturedValue output,
                            String errorCode,
                            List<Step> steps,
                            UUID versionId,
                            String versionLabel,
                            boolean stepsTruncated,
                            ScenarioRef scenario,
                            ScenarioCheck scenarioCheck,
                            ResolvedFixture fixture) {
    public ControlledRun(UUID requestId,
                         UUID runId,
                         UUID draftId,
                         UUID revisionId,
                         long revision,
                         String actor,
                         String runtimeContext,
                         String applicationBuild,
                         String mode,
                         String catalogFingerprint,
                         String artifactHash,
                         String outcome,
                         Instant startedAt,
                         Instant endedAt,
                         CapturedValue input,
                         CapturedValue output,
                         String errorCode,
                         List<Step> steps,
                         UUID versionId,
                         String versionLabel,
                         boolean stepsTruncated) {
        this(requestId, runId, draftId, revisionId, revision, actor, runtimeContext, applicationBuild, mode,
                catalogFingerprint, artifactHash, outcome, startedAt, endedAt, input, output, errorCode, steps,
                versionId, versionLabel, stepsTruncated, null, null, null);
    }

    public ControlledRun withScenario(ScenarioRef reference, ScenarioCheck check) {
        return new ControlledRun(requestId, runId, draftId, revisionId, revision, actor, runtimeContext,
                applicationBuild, mode, catalogFingerprint, artifactHash, outcome, startedAt, endedAt,
                input, output, errorCode, steps, versionId, versionLabel, stepsTruncated, reference, check, fixture);
    }

    public ControlledRun withFixture(ResolvedFixture resolved) {
        return new ControlledRun(requestId, runId, draftId, revisionId, revision, actor, runtimeContext,
                applicationBuild, mode, catalogFingerprint, artifactHash, outcome, startedAt, endedAt,
                input, output, errorCode, steps, versionId, versionLabel, stepsTruncated, scenario, scenarioCheck,
                resolved);
    }

    public ControlledRun(UUID requestId,
                         UUID runId,
                         UUID draftId,
                         UUID revisionId,
                         long revision,
                         String actor,
                         String runtimeContext,
                         String applicationBuild,
                         String mode,
                         String catalogFingerprint,
                         String artifactHash,
                         String outcome,
                         Instant startedAt,
                         Instant endedAt,
                         CapturedValue input,
                         CapturedValue output,
                         String errorCode,
                         List<Step> steps,
                         UUID versionId,
                         String versionLabel) {
        this(requestId, runId, draftId, revisionId, revision, actor, runtimeContext, applicationBuild, mode,
                catalogFingerprint, artifactHash, outcome, startedAt, endedAt, input, output, errorCode, steps,
                versionId, versionLabel, false);
    }

    public ControlledRun(UUID requestId,
                         UUID runId,
                         UUID draftId,
                         UUID revisionId,
                         long revision,
                         String actor,
                         String runtimeContext,
                         String applicationBuild,
                         String mode,
                         String catalogFingerprint,
                         String artifactHash,
                         String outcome,
                         Instant startedAt,
                         Instant endedAt,
                         CapturedValue input,
                         CapturedValue output,
                         String errorCode,
                         List<Step> steps) {
        this(requestId, runId, draftId, revisionId, revision, actor, runtimeContext, applicationBuild, mode,
                catalogFingerprint, artifactHash, outcome, startedAt, endedAt, input, output, errorCode, steps, null,
                null);
    }

    public ControlledRun withVersion(StudioVersion version) {
        return new ControlledRun(requestId, runId, draftId, revisionId, revision, actor, runtimeContext,
                applicationBuild, mode, catalogFingerprint, artifactHash, outcome, startedAt, endedAt,
                input, output, errorCode, steps, version.id(), version.label(), stepsTruncated, scenario,
                scenarioCheck, fixture);
    }

    public ControlledRun {
        steps = List.copyOf(steps);
    }

    public record Step(UUID id,
                       String nodeId,
                       String status,
                       Instant startedAt,
                       Instant endedAt,
                       UUID runId,
                       UUID parentId,
                       String branchId,
                       String errorCode) {
        public Step(UUID id, String nodeId, String status, Instant startedAt, Instant endedAt) {
            this(id, nodeId, status, startedAt, endedAt, null, null, null, null);
        }
    }
}
