package io.github.gear4jtest.external.api.consistency;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.github.gear4jtest.core.persistence.PageRequest;
import io.github.gear4jtest.external.api.repository.OperationChainConfigRepository;
import io.github.gear4jtest.external.api.repository.OperationChainNotFoundException;
import io.github.gear4jtest.external.api.repository.OperationChainPublicationRepository;
import io.github.gear4jtest.external.api.repository.OperationChainPublicationStage;
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

    private final OperationChainConfigRepository configRepository;
    private final OperationChainPublicationRepository publicationRepository;
    private final ArtifactStoreProvider storeProvider;
    private final int pageSize;

    public ArtifactPublicationReconciler(OperationChainConfigRepository configRepository,
                                         OperationChainPublicationRepository publicationRepository,
                                         ArtifactStoreProvider storeProvider) {
        this(configRepository, publicationRepository, storeProvider, DEFAULT_PAGE_SIZE);
    }

    public ArtifactPublicationReconciler(OperationChainConfigRepository configRepository,
                                         OperationChainPublicationRepository publicationRepository,
                                         ArtifactStoreProvider storeProvider,
                                         int pageSize) {
        this.configRepository = Objects.requireNonNull(configRepository, "configRepository must not be null");
        this.publicationRepository = Objects.requireNonNull(publicationRepository,
                                                            "publicationRepository must not be null");
        this.storeProvider = Objects.requireNonNull(storeProvider, "storeProvider must not be null");
        if (!publicationRepository.supportsStaging()) {
            throw new IllegalArgumentException("publicationRepository must support staged publication");
        }
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be > 0");
        }
        this.pageSize = pageSize;
    }

    public Report reconcileOlderThan(Duration minimumAge) {
        Objects.requireNonNull(minimumAge, "minimumAge must not be null");
        if (minimumAge.isNegative()) {
            throw new IllegalArgumentException("minimumAge must not be negative");
        }
        return reconcileStagedBefore(Instant.now().minus(minimumAge));
    }

    public Report reconcileStagedBefore(Instant cutoff) {
        Objects.requireNonNull(cutoff, "cutoff must not be null");
        List<OperationChainPublicationStage> stages = loadStages(cutoff);
        int committed = 0;
        int aborted = 0;
        List<Failure> failures = new ArrayList<>();

        for (OperationChainPublicationStage stage : stages) {
            try {
                var config = configRepository.findByAssemblyLineId(stage.object().alId())
                        .orElseThrow(() -> new OperationChainNotFoundException(
                                "Config not found for alId=" + stage.object().alId()));
                String currentFingerprint = ArtifactStoreConfigurationFingerprint.from(config);
                if (!Objects.equals(currentFingerprint, stage.storeFingerprint())) {
                    throw new IllegalStateException("Artifact-store configuration changed for alId="
                            + stage.object().alId() + "; staged publication retained");
                }
                var store = storeProvider.forConfig(config);
                if (store.exists(stage.object().contentHash())) {
                    publicationRepository.commit(stage.stageId());
                    committed++;
                } else if (publicationRepository.abortIfUnchanged(stage)) {
                    aborted++;
                }
            } catch (IOException | RuntimeException exception) {
                failures.add(new Failure(stage.stageId(), stage.object().alId(), stage.object().version(),
                        exception.getMessage(), exception));
            }
        }
        return new Report(stages.size(), committed, aborted, List.copyOf(failures));
    }

    private List<OperationChainPublicationStage> loadStages(Instant cutoff) {
        List<OperationChainPublicationStage> stages = new ArrayList<>();
        int offset = 0;
        while (true) {
            List<OperationChainPublicationStage> page = publicationRepository.findStagedBefore(cutoff,
                                                                                               new PageRequest(offset,
                                                                                                       pageSize));
            stages.addAll(page);
            if (page.size() < pageSize) {
                return List.copyOf(stages);
            }
            offset += page.size();
        }
    }

    public record Report(int stagesChecked, int committed, int aborted, List<Failure> failures) {
        public Report {
            if (stagesChecked < 0 || committed < 0 || aborted < 0) {
                throw new IllegalArgumentException("reconciliation counts must not be negative");
            }
            if (committed + aborted > stagesChecked) {
                throw new IllegalArgumentException("resolved stages must not exceed stagesChecked");
            }
            failures = List.copyOf(Objects.requireNonNull(failures, "failures must not be null"));
        }

        /**
         * Number of stages intentionally kept for a later reconciliation pass.
         */
        public int retained() {
            return stagesChecked - committed - aborted;
        }

        public boolean successful() {
            return failures.isEmpty();
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
            return successful() && retained() == 0;
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
