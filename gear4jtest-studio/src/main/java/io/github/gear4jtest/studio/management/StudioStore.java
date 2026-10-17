package io.github.gear4jtest.studio.management;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import io.github.gear4jtest.studio.model.DraftRevision;
import io.github.gear4jtest.studio.model.StudioPublication;
import io.github.gear4jtest.studio.model.StudioVersion;

/**
 * Trusted persistence boundary. Reads provide a consistent snapshot; writes
 * commit all changes and audit atomically, or roll back. No caller may retain a
 * Session beyond its callback. Mutations of an existing domain or its
 * descendants must first lockDomain on that domain. Independent run snapshots
 * instead use lockRun; never acquire a domain lock while holding a run lock.
 */
public interface StudioStore {
    /**
     * Atomically binds an empty store, or rejects a different stable runtime
     * identity.
     */
    void bindContext(String runtimeContextId);

    <T> T read(Function<Session, T> work);

    <T> T write(Function<Session, T> work);

    interface Session {
        void lockDomain(UUID domainId);

        ManagedResource resource(UUID id);

        List<StudioDomain> domains(String after, int limit);

        List<StudioChain> chains(UUID domainId, String after, int limit);

        List<StudioDraft> drafts(UUID chainId, String after, int limit);

        void insert(ManagedResource resource);

        void update(ManagedResource resource, long expectedVersion);

        List<AccessGrant> grants(UUID resourceId);

        void replaceGrants(UUID resourceId, List<AccessGrant> grants);

        DraftRevision revision(UUID draftId, long number);

        List<DraftRevision> revisions(UUID draftId, long after, int limit);

        void appendRevision(DraftRevision revision);

        void insertVersion(StudioVersion version);

        StudioVersion version(UUID versionId);

        io.github.gear4jtest.studio.model.StudioScenario scenario(UUID id);

        List<io.github.gear4jtest.studio.model.StudioScenario> scenarios(UUID resourceId, String after, int limit);

        void insertScenario(io.github.gear4jtest.studio.model.StudioScenario scenario);

        void updateScenario(io.github.gear4jtest.studio.model.StudioScenario scenario, long expectedVersion);

        void appendScenarioRevision(io.github.gear4jtest.studio.model.ScenarioRevision revision);

        io.github.gear4jtest.studio.model.ScenarioRevision scenarioRevision(UUID id, long number);

        List<io.github.gear4jtest.studio.model.ScenarioRevision> scenarioRevisions(UUID id, long after, int limit);

        List<StudioVersion> versions(UUID chainId, String after, int limit);

        StudioPublication publication(UUID versionId, StudioPublication.Mode mode);

        /**
         * Domain lock required; replaces only the lifecycle result, never the version.
         */
        void savePublication(StudioPublication publication);

        io.github.gear4jtest.studio.model.StudioRun run(UUID id);

        /** Locks and returns one observation for update, or null when absent. */
        io.github.gear4jtest.studio.model.StudioRun lockRun(UUID id);

        List<UUID> staleCandidates(InstantCutoff cutoff);

        int purgeStaleRuns(InstantCutoff cutoff);

        /** Scope is one chain or draft; SQL filters precede bounded ACL scanning. */
        List<io.github.gear4jtest.studio.model.StudioRun> runs(UUID chainId,
                                                               UUID draftId,
                                                               io.github.gear4jtest.studio.model.RunFilter filter,
                                                               String after,
                                                               int limit);

        /**
         * Domain lock on admission, run lock on update. Provenance and scope are
         * immutable.
         */
        void saveRun(io.github.gear4jtest.studio.model.StudioRun run);

        /**
         * Trusted host maintenance: only terminal rows older than the retention cutoff.
         */
        int purgeRuns(InstantCutoff cutoff);

        void appendAudit(AuditEvent event);

        List<AuditEvent> audit(UUID domainId, UUID chainId, UUID draftId, long after, int limit);
    }

    record InstantCutoff(java.time.Instant before, int limit) {
        public InstantCutoff {
            if (before == null || limit < 1 || limit > 100)
                throw new IllegalArgumentException("Invalid purge limit");
        }
    }
}
