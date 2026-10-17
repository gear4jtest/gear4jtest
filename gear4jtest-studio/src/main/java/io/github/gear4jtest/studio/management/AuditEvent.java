package io.github.gear4jtest.studio.management;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only management metadata; never contains definitions, inputs, outputs
 * or arbitrary messages.
 */
public record AuditEvent(long sequence,
                         UUID eventId,
                         UUID domainId,
                         UUID chainId,
                         UUID draftId,
                         ResourceKind resourceKind,
                         UUID resourceId,
                         long resourceVersion,
                         String actor,
                         Action action,
                         Instant occurredAt,
                         UUID revisionId,
                         UUID requestId,
                         AccessPolicy accessBefore,
                         AccessPolicy accessAfter,
                         UUID versionId,
                         UUID attemptId) {
    public AuditEvent(long sequence,
                      UUID eventId,
                      UUID domainId,
                      UUID chainId,
                      UUID draftId,
                      ResourceKind resourceKind,
                      UUID resourceId,
                      long resourceVersion,
                      String actor,
                      Action action,
                      Instant occurredAt,
                      UUID revisionId,
                      UUID requestId,
                      AccessPolicy accessBefore,
                      AccessPolicy accessAfter,
                      UUID versionId) {
        this(sequence, eventId, domainId, chainId, draftId, resourceKind, resourceId, resourceVersion, actor,
                action, occurredAt, revisionId, requestId, accessBefore, accessAfter, versionId, null);
    }

    public AuditEvent(long sequence,
                      UUID eventId,
                      UUID domainId,
                      UUID chainId,
                      UUID draftId,
                      ResourceKind resourceKind,
                      UUID resourceId,
                      long resourceVersion,
                      String actor,
                      Action action,
                      Instant occurredAt,
                      UUID revisionId,
                      UUID requestId,
                      AccessPolicy accessBefore,
                      AccessPolicy accessAfter) {
        this(sequence, eventId, domainId, chainId, draftId, resourceKind, resourceId, resourceVersion, actor, action,
                occurredAt, revisionId, requestId, accessBefore, accessAfter, null);
    }

    public enum Action {
        DOMAIN_CREATED, CHAIN_CREATED, DRAFT_CREATED, METADATA_UPDATED, REVISION_SAVED,
        ACCESS_CHANGED, DOMAIN_ARCHIVED, CHAIN_ARCHIVED, DRAFT_ABANDONED, TEST_REQUESTED, VERSION_CREATED,
        PUBLICATION_REQUESTED, PROMOTION_REQUESTED,
        PUBLICATION_CONFIRMED, PROMOTION_CONFIRMED, PUBLICATION_REJECTED, PUBLICATION_UNKNOWN,
        PROMOTION_REJECTED, PROMOTION_UNKNOWN
    }
}
