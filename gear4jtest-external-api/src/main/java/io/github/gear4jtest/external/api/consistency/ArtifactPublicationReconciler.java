package io.github.gear4jtest.external.api.consistency;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.github.gear4jtest.core.persistence.PageRequest;
import io.github.gear4jtest.external.api.artifact.ArtifactStore;
import io.github.gear4jtest.external.api.repository.OperationChainConfigRepository;
import io.github.gear4jtest.external.api.repository.OperationChainNotFoundException;
import io.github.gear4jtest.external.api.repository.OperationChainPublicationRepository;
import io.github.gear4jtest.external.api.repository.OperationChainPublicationStage;
import io.github.gear4jtest.external.api.repository.OperationChainPublicationStageCursor;
import io.github.gear4jtest.external.api.spi.ArtifactStoreProvider;
import io.github.gear4jtest.external.api.storage.ArtifactStoreConfigurationFingerprint;

/**
 * Reconciles durable publication stages left by process crashes or transient
 * failures.
 *
 * <p>
 * A stage whose artifact exists is committed. A stage whose artifact is still
 * missing after the caller-selected grace period is aborted. Failures leave the
 * stage untouched for a later retry.
 * </p>
 */
public final class ArtifactPublicationReconciler {
    private static final int DEFAULT_PAGE_SIZE = 250;
    private static final int DEFAULT_MAX_STAGES_PER_RUN = 10_000;
    private static final int DEFAULT_MAX_REPORTED_FAILURES = 1_000;

    private final OperationChainConfigRepository configRepository;
    private final OperationChainPublicationRepository publicationRepository;
    private final ArtifactStoreProvider storeProvider;
    private final int pageSize;
    private final int maxStagesPerRun;
    private final int maxReportedFailures;

    public ArtifactPublicationReconciler(OperationChainConfigRepository configRepository,
                                         OperationChainPublicationRepository publicationRepository,
                                         ArtifactStoreProvider storeProvider) {
        this(configRepository, publicationRepository, storeProvider, DEFAULT_PAGE_SIZE, DEFAULT_MAX_STAGES_PER_RUN,
                DEFAULT_MAX_REPORTED_FAILURES);
    }

    public ArtifactPublicationReconciler(OperationChainConfigRepository configRepository,
                                         OperationChainPublicationRepository publicationRepository,
                                         ArtifactStoreProvider storeProvider,
                                         int pageSize) {
        this(configRepository, publicationRepository, storeProvider, pageSize, DEFAULT_MAX_STAGES_PER_RUN,
                DEFAULT_MAX_REPORTED_FAILURES);
    }

    public ArtifactPublicationReconciler(OperationChainConfigRepository configRepository,
                                         OperationChainPublicationRepository publicationRepository,
                                         ArtifactStoreProvider storeProvider,
                                         int pageSize,
                                         int maxStagesPerRun,
                                         int maxReportedFailures) {
        this.configRepository = Objects.requireNonNull(configRepository, "configRepository must not be null");
        this.publicationRepository = Objects.requireNonNull(publicationRepository,
                                                            "publicationRepository must not be null");
        this.storeProvider = Objects.requireNonNull(storeProvider, "storeProvider must not be null");
        if (!publicationRepository.supportsStaging()) {
            throw new IllegalArgumentException("publicationRepository must support staged publication");
        }
        if (pageSize <= 0 || pageSize > PageRequest.MAX_LIMIT) {
            throw new IllegalArgumentException("pageSize must be between 1 and " + PageRequest.MAX_LIMIT);
        }
        if (maxStagesPerRun <= 0) {
            throw new IllegalArgumentException("maxStagesPerRun must be > 0");
        }
        if (maxReportedFailures <= 0) {
            throw new IllegalArgumentException("maxReportedFailures must be > 0");
        }
        this.pageSize = pageSize;
        this.maxStagesPerRun = maxStagesPerRun;
        this.maxReportedFailures = maxReportedFailures;
    }

    public Report reconcileOlderThan(Duration minimumAge) {
        Objects.requireNonNull(minimumAge, "minimumAge must not be null");
        if (minimumAge.isNegative()) {
            throw new IllegalArgumentException("minimumAge must not be negative");
        }
        return reconcileStagedBefore(Instant.now().minus(minimumAge));
    }

    public Report reconcileStagedBefore(Instant cutoff) {
        return reconcileStagedBefore(cutoff, null);
    }

    /**
     * Reconciles one bounded pass after an optional continuation cursor. Callers
     * continuing a pass must reuse the same cutoff.
     */
    public Report reconcileStagedBefore(Instant cutoff, OperationChainPublicationStageCursor after) {
        Objects.requireNonNull(cutoff, "cutoff must not be null");
        int stagesChecked = 0;
        int committed = 0;
        int aborted = 0;
        int failuresOmitted = 0;
        boolean complete = false;
        OperationChainPublicationStageCursor cursor = after;
        List<Failure> failures = new ArrayList<>();
        Map<String, ArtifactStore> storesByFingerprint = new LinkedHashMap<>();

        try {
            while (stagesChecked < maxStagesPerRun) {
                int limit = Math.min(pageSize, maxStagesPerRun - stagesChecked);
                List<OperationChainPublicationStage> page = publicationRepository.findStagedAfter(cutoff, cursor,
                                                                                                  limit);
                if (page.isEmpty()) {
                    complete = true;
                    break;
                }
                for (OperationChainPublicationStage stage : page) {
                    stagesChecked++;
                    cursor = OperationChainPublicationStageCursor.after(stage);
                    try {
                        var config = configRepository.findByAssemblyLineId(stage.object().alId())
                                .orElseThrow(() -> new OperationChainNotFoundException(
                                        "Config not found for alId=" + stage.object().alId()));
                        String currentFingerprint = ArtifactStoreConfigurationFingerprint.from(config);
                        if (!Objects.equals(currentFingerprint, stage.storeFingerprint())) {
                            throw new IllegalStateException("Artifact-store configuration changed for alId="
                                    + stage.object().alId() + "; staged publication retained");
                        }
                        ArtifactStore store = storesByFingerprint.computeIfAbsent(currentFingerprint,
                                                                                  ignored -> Objects
                                                                                          .requireNonNull(storeProvider
                                                                                                  .forConfig(config),
                                                                                                          "storeProvider returned null"));
                        if (store.exists(stage.object().contentHash())) {
                            publicationRepository.commit(stage.stageId());
                            committed++;
                        } else if (publicationRepository.abortIfUnchanged(stage)) {
                            aborted++;
                        }
                    } catch (IOException | RuntimeException exception) {
                        Failure failure = new Failure(stage.stageId(), stage.object().alId(), stage.object().version(),
                                exception.getMessage(), exception);
                        if (failures.size() < maxReportedFailures) {
                            failures.add(failure);
                        } else {
                            failuresOmitted++;
                        }
                    }
                }
                if (page.size() < limit) {
                    complete = true;
                    break;
                }
            }
            if (!complete && publicationRepository.findStagedAfter(cutoff, cursor, 1).isEmpty()) {
                complete = true;
            }
        } finally {
            storesByFingerprint.values().forEach(storeProvider::release);
        }
        return new Report(stagesChecked, committed, aborted, List.copyOf(failures), complete,
                complete ? null : cursor, failuresOmitted);
    }

    public record Report(int stagesChecked,
                         int committed,
                         int aborted,
                         List<Failure> failures,
                         boolean complete,
                         OperationChainPublicationStageCursor nextCursor,
                         int failuresOmitted) {
        public Report(int stagesChecked, int committed, int aborted, List<Failure> failures) {
            this(stagesChecked, committed, aborted, failures, true, null, 0);
        }

        public Report {
            if (stagesChecked < 0 || committed < 0 || aborted < 0 || failuresOmitted < 0) {
                throw new IllegalArgumentException("reconciliation counts must not be negative");
            }
            if (committed + aborted > stagesChecked) {
                throw new IllegalArgumentException("resolved stages must not exceed stagesChecked");
            }
            failures = List.copyOf(Objects.requireNonNull(failures, "failures must not be null"));
            if ((complete && nextCursor != null) || (!complete && nextCursor == null)) {
                throw new IllegalArgumentException("nextCursor must be present exactly when the report is incomplete");
            }
        }

        /**
         * Number of stages intentionally kept for a later reconciliation pass.
         */
        public int retained() {
            return stagesChecked - committed - aborted;
        }

        public boolean successful() {
            return failures.isEmpty() && failuresOmitted == 0;
        }

        /**
         * Whether every inspected stage was either committed or aborted.
         *
         * <p>
         * A report can be {@link #successful()} but not fully reconciled when a
         * concurrent publication renewed a stage while reconciliation was in progress.
         * </p>
         */
        public boolean fullyReconciled() {
            return complete && successful() && retained() == 0;
        }
    }

    public record Failure(String stageId,
                          String assemblyLineId,
                          String version,
                          String message,
                          Throwable cause) {
        public Failure {
            Objects.requireNonNull(stageId, "stageId must not be null");
            Objects.requireNonNull(assemblyLineId, "assemblyLineId must not be null");
            Objects.requireNonNull(version, "version must not be null");
        }
    }
}
