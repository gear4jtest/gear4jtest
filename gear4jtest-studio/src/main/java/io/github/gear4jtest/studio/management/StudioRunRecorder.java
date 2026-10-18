package io.github.gear4jtest.studio.management;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import io.github.gear4jtest.studio.model.RunObservation;
import io.github.gear4jtest.studio.model.StudioRun;
import io.github.gear4jtest.studio.service.StudioProblem;

import static io.github.gear4jtest.studio.service.StudioProblem.Code.*;

/**
 * Trusted host write port, never exposed by the management facade or HTTP. The
 * host must apply its capture/redaction policy before supplying an observation.
 */
public final class StudioRunRecorder {
    private final StudioStore store;
    private final String contextId;
    private final Clock clock;

    public StudioRunRecorder(StudioStore store, String runtimeContextId, Clock clock) {
        this.store = Objects.requireNonNull(store);
        contextId = Objects.requireNonNull(runtimeContextId);
        this.clock = Objects.requireNonNull(clock);
        store.bindContext(contextId);
    }

    /**
     * Register an application-initiated run, including a programmatically built
     * chain.
     */
    public UUID beginHostRun(UUID chainId, StudioRun.Provenance provenance) {
        if (provenance.draftId() != null || provenance.revisionId() != null || provenance.requestId() != null
                || provenance.scenario() != null || provenance.fixture() != null)
            throw new IllegalArgumentException("Host run is not a Studio test receipt");
        UUID id = UUID.randomUUID();
        store.write(tx -> {
            begin(tx, id, chainId, StudioRun.Origin.HOST_RUN, provenance);
            return null;
        });
        return id;
    }

    void begin(StudioStore.Session tx, UUID id, UUID scope, StudioRun.Origin origin, StudioRun.Provenance provenance) {
        if (!contextId.equals(provenance.runtimeContextId()) || provenance.actor() == null
                || provenance.actor().length() > 255 || !Set.of("TEST", "RUN").contains(provenance.mode())
                || provenance.artifactHash() != null && !provenance.artifactHash().matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException("Invalid run provenance");
        var resource = ResourceAccess.resource(tx, scope);
        tx.lockDomain(ResourceAccess.domain(tx, resource).id());
        var chain = resource instanceof StudioDraft draft ? ResourceAccess.chain(tx, draft)
                : resource instanceof StudioChain c ? c : null;
        if (chain == null || !chain.runtimeAssemblyLineId().equals(provenance.runtimeAssemblyLineId()))
            throw new IllegalArgumentException("Run does not match the registered chain");
        if (provenance.versionId() != null) {
            var version = tx.version(provenance.versionId());
            if (version == null || !version.chainId().equals(chain.id())
                    || !version.artifactHash().equals(provenance.artifactHash())
                    || !version.label().equals(provenance.versionLabel()))
                throw new IllegalArgumentException("Version provenance mismatch");
        }
        if (tx.run(id) != null)
            throw new StudioProblem(CONFLICT);
        var now = now();
        tx.saveRun(new StudioRun(id, scope, chain.id(), origin, provenance, "PREPARING", now, now, null, null, 0,
                null));
    }

    /**
     * Serialized by this run's row lock; a terminal observation cannot be
     * overwritten.
     */
    public void record(UUID id, RunObservation observation) {
        Objects.requireNonNull(observation);
        store.write(tx -> {
            var current = tx.lockRun(id);
            if (current == null)
                throw new StudioProblem(NOT_FOUND);
            if (!current.status().equals("PREPARING") && !current.status().equals("RUNNING"))
                return null;
            var provenance = current.provenance();
            if (provenance.artifactHash() != null && observation.artifactHash() != null
                    && !provenance.artifactHash().equals(observation.artifactHash())
                    || current.observation() != null && current.observation().runId() != null
                            && observation.runId() != null
                            && !current.observation().runId().equals(observation.runId()))
                throw new IllegalArgumentException("Observation provenance changed");
            if (provenance.artifactHash() == null && observation.artifactHash() != null)
                provenance = provenance.withArtifactHash(observation.artifactHash());
            tx.saveRun(new StudioRun(current.id(), current.resourceId(), current.chainId(), current.origin(),
                    provenance,
                    observation.status(), current.createdAt(), now(), observation.startedAt(), observation.endedAt(),
                    current.sequence() + 1, observation));
            return null;
        });
    }

    /**
     * Bounded maintenance. Running/preparing observations are never automatically
     * deleted.
     */
    public int purgeTerminalBefore(Instant before, int limit) {
        if (before == null || before.isAfter(clock.instant()))
            throw new IllegalArgumentException("Invalid retention cutoff");
        return store.write(tx -> tx.purgeRuns(new StudioStore.InstantCutoff(before, limit)));
    }

    /**
     * Marks observation freshness only; never infers a business outcome or
     * termination.
     */
    public int markStaleBefore(Instant before, int limit) {
        cutoff(before);
        return store.write(tx -> {
            int marked = 0;
            for (var id : tx.staleCandidates(new StudioStore.InstantCutoff(before, limit))) {
                var run = tx.lockRun(id);
                if (run != null && run.staleAt() == null && run.updatedAt().isBefore(before)
                        && Set.of("PREPARING", "RUNNING").contains(run.status())) {
                    tx.saveRun(new StudioRun(run.id(), run.resourceId(), run.chainId(), run.origin(), run.provenance(),
                            run.status(), run.createdAt(), run.updatedAt(), run.startedAt(), run.endedAt(),
                            run.sequence() + 1, run.observation(), now()));
                    marked++;
                }
            }
            return marked;
        });
    }

    /**
     * Explicit host retention of stale observations after an additional grace
     * period.
     */
    public int purgeStaleBefore(Instant before, int limit) {
        cutoff(before);
        return store.write(tx -> tx.purgeStaleRuns(new StudioStore.InstantCutoff(before, limit)));
    }

    private void cutoff(Instant before) {
        if (before == null || before.isAfter(clock.instant()))
            throw new IllegalArgumentException("Invalid maintenance cutoff");
    }

    private Instant now() {
        return Instant.ofEpochMilli(clock.millis());
    }
}
