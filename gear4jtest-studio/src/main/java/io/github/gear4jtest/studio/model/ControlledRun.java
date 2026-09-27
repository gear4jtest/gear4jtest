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
                            List<Step> steps) {
    public ControlledRun {
        steps = List.copyOf(steps);
    }

    public record Step(UUID id, String nodeId, String status, Instant startedAt, Instant endedAt) {}
}
