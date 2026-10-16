package io.github.gear4jtest.studio.management;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import io.github.gear4jtest.studio.model.DraftRevision;

/**
 * Trusted persistence boundary. Reads provide a consistent snapshot; writes
 * commit all changes and audit atomically, or roll back. No caller may retain a
 * Session beyond its callback. Mutations of an existing domain or its
 * descendants must first lockDomain on that domain.
 */
public interface StudioStore {
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

        void appendAudit(AuditEvent event);

        List<AuditEvent> audit(UUID domainId, UUID chainId, UUID draftId, long after, int limit);
    }
}
