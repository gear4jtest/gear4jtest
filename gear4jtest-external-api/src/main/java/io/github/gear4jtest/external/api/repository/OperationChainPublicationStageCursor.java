package io.github.gear4jtest.external.api.repository;

import java.time.Instant;
import java.util.Objects;

/**
 * Stable continuation key for publication stages ordered by age and identifier.
 */
public record OperationChainPublicationStageCursor(Instant stagedAt, String stageId) {
    public OperationChainPublicationStageCursor {
        Objects.requireNonNull(stagedAt, "stagedAt must not be null");
        if (stageId == null || stageId.isBlank()) {
            throw new IllegalArgumentException("stageId must not be blank");
        }
    }

    public static OperationChainPublicationStageCursor after(OperationChainPublicationStage stage) {
        Objects.requireNonNull(stage, "stage must not be null");
        return new OperationChainPublicationStageCursor(stage.stagedAt(), stage.stageId());
    }
}
