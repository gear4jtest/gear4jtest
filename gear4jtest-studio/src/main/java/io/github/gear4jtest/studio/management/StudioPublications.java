package io.github.gear4jtest.studio.management;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

import io.github.gear4jtest.studio.model.*;
import io.github.gear4jtest.studio.service.StudioProblem;
import io.github.gear4jtest.studio.spi.StudioPublisher;
import io.github.gear4jtest.studio.spi.StudioRuntime;

import static io.github.gear4jtest.studio.management.ResourceAccess.*;
import static io.github.gear4jtest.studio.model.StudioPublication.State.*;
import static io.github.gear4jtest.studio.service.StudioProblem.Code.*;

/**
 * Synchronous lifecycle use cases; only publication intent/results are durable.
 */
final class StudioPublications {
    private final StudioStore store;
    private final StudioRuntime runtime;
    private final StudioPublisher publisher;
    private final Clock clock;

    StudioPublications(StudioStore store, StudioRuntime runtime, StudioPublisher publisher, Clock clock) {
        this.store = store;
        this.runtime = runtime;
        this.publisher = publisher;
        this.clock = clock;
    }

    VersionState freeze(StudioIdentity actor, UUID draftId, long number, String label) {
        capability(StudioPublication.Mode.TEST);
        if (label == null || !label.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}") || label.equalsIgnoreCase("latest"))
            throw new StudioProblem(INVALID_REQUEST);
        var selected = store.read(tx -> {
            var draft = draft(tx, draftId);
            requireRead(tx, actor, draft, Permission.READ);
            var chain = ResourceAccess.chain(tx, draft);
            requireRead(tx, actor, chain, Permission.PUBLISH);
            var revision = tx.revision(draftId, number);
            if (revision == null)
                throw new StudioProblem(NOT_FOUND);
            var definition = revision.requireDefinition();
            if (!definition.id().equals(chain.runtimeAssemblyLineId()))
                throw new StudioProblem(INVALID_DEFINITION);
            return revision;
        });
        var definition = selected.requireDefinition();
        runtime.assertSafeToStore(definition);
        var validation = runtime.validate(definition);
        if (!validation.valid())
            throw new StudioProblem(INVALID_DEFINITION);
        String source = runtime.exportDefinition(definition, "application/xml");
        var format = runtime.formatAdapter("application/xml");
        var context = runtime.context();
        if (!context.catalogFingerprint().equals(validation.catalogFingerprint()))
            throw new StudioProblem(CONFLICT);
        return store.write(tx -> {
            var draft = draft(tx, draftId);
            tx.lockDomain(domain(tx, draft).id());
            draft = draft(tx, draftId);
            requireRead(tx, actor, draft, Permission.READ);
            if (draft.abandoned())
                throw new StudioProblem(ARCHIVED);
            var chain = ResourceAccess.chain(tx, draft);
            requireRead(tx, actor, chain, Permission.PUBLISH);
            var version = new StudioVersion(UUID.randomUUID(), chain.id(), draftId, selected.revisionId(),
                    selected.number(), chain.runtimeAssemblyLineId(), label, actor.principalId(), now(), context.id(),
                    context.applicationBuild(), context.catalogFingerprint(), "application/xml", hash(source), source,
                    definition.schemaVersion(), format.id(), format.version());
            tx.insertVersion(version);
            audit(tx, actor.principalId(), chain, version, AuditEvent.Action.VERSION_CREATED, null);
            return view(tx, actor, version);
        });
    }

    VersionState get(StudioIdentity actor, UUID id) {
        return store.read(tx -> view(tx, actor, version(tx, id)));
    }

    StudioPage<VersionState> list(StudioIdentity actor, UUID chainId, String after, int limit) {
        if (limit < 1 || limit > 100)
            throw new StudioProblem(INVALID_REQUEST);
        if (after != null) {
            try {
                UUID.fromString(after);
            } catch (IllegalArgumentException e) {
                throw new StudioProblem(INVALID_REQUEST);
            }
        }
        return store.read(tx -> {
            var chain = chain(tx, chainId);
            requireRead(tx, actor, chain, Permission.READ);
            var versions = tx.versions(chainId, after, limit + 1);
            boolean more = versions.size() > limit;
            var page = more ? versions.subList(0, limit) : versions;
            return new StudioPage<>(page.stream().map(v -> view(tx, actor, v)).toList(),
                    more ? page.get(page.size() - 1).id().toString() : null);
        });
    }

    DefinitionComparison compare(StudioIdentity actor, UUID firstId, UUID secondId) {
        return store.read(tx -> {
            var first = version(tx, firstId);
            var second = version(tx, secondId);
            view(tx, actor, first);
            view(tx, actor, second);
            if (!first.chainId().equals(second.chainId()))
                throw new StudioProblem(INVALID_REQUEST);
            return new DefinitionComparison(first.revisionId(), DefinitionDiff.between(
                                                                                       tx.revision(first.draftId(),
                                                                                                   first.revision())
                                                                                               .requireDefinition(),
                                                                                       tx.revision(second.draftId(),
                                                                                                   second.revision())
                                                                                               .requireDefinition()),
                    first.source(), second.source());
        });
    }

    VersionState publish(StudioIdentity actor, UUID id, StudioPublication.Mode mode) {
        capability(mode);
        var attempt = store.write(tx -> {
            var version = version(tx, id);
            tx.lockDomain(domain(tx, chain(tx, version.chainId())).id());
            var chain = chain(tx, version.chainId());
            requireRead(tx, actor, chain, permission(mode));
            var old = tx.publication(id, mode);
            if (old != null && (old.state() == CONFIRMED || old.state() == REQUESTED))
                return new Attempt(version, old, false);
            if (mode == StudioPublication.Mode.RUN) {
                var test = tx.publication(id, StudioPublication.Mode.TEST);
                if (test == null || test.state() != CONFIRMED)
                    throw new StudioProblem(TEST_PUBLICATION_REQUIRED);
            }
            var requested = new StudioPublication(id, mode, REQUESTED,
                    actor.principalId(), now(),
                    null, null, null, null);
            tx.savePublication(requested);
            audit(tx, actor.principalId(), chain, version, mode == StudioPublication.Mode.TEST
                    ? AuditEvent.Action.PUBLICATION_REQUESTED : AuditEvent.Action.PROMOTION_REQUESTED,
                  requested.attemptId());
            return new Attempt(version, requested, true);
        });
        if (!attempt.execute())
            return get(actor, id);
        StudioProblem.Code failure = null;
        try {
            if (mode == StudioPublication.Mode.TEST)
                publisher.publishTest(attempt.version(), actor.principalId());
            else {
                if (observed(attempt.version(), StudioPublication.Mode.TEST) == null)
                    throw new StudioProblem(TEST_PUBLICATION_REQUIRED);
                publisher.promoteToRun(attempt.version(), actor.principalId());
            }
        } catch (StudioProblem e) {
            failure = e.code();
        } catch (RuntimeException e) {
            // Never forward provider exception text or assume that a write failed.
        }
        completeFromObservation(actor, attempt.version(), attempt.publication(), failure, false);
        return get(actor, id);
    }

    VersionState refresh(StudioIdentity actor, UUID id) {
        if (publisher == null)
            throw new StudioProblem(FORBIDDEN);
        var state = get(actor, id);
        for (var publication : state.publications())
            completeFromObservation(actor, state.version(), publication, null, true);
        return get(actor, id);
    }

    private void completeFromObservation(StudioIdentity actor,
                                         StudioVersion version,
                                         StudioPublication expected,
                                         StudioProblem.Code failure,
                                         boolean refresh) {
        var mode = expected.mode();
        PublishedArtifact observation = null;
        try {
            observation = observed(version, mode);
        } catch (StudioProblem e) {
            failure = e.code();
        } catch (RuntimeException unavailable) {
            failure = null;
        }
        boolean rejected = failure == PUBLICATION_REJECTED || failure == PUBLICATION_CONFLICT
                || failure == TEST_PUBLICATION_REQUIRED;
        var state = observation != null ? CONFIRMED : rejected ? REJECTED : UNKNOWN;
        String error = observation != null ? null : rejected ? failure.name() : "PUBLICATION_OUTCOME_UNKNOWN";
        var result = observation;
        store.write(tx -> {
            var chain = chain(tx, version.chainId());
            tx.lockDomain(chain.domainId());
            var current = tx.publication(version.id(), mode);
            if (current == null)
                throw new StudioProblem(STORAGE_UNAVAILABLE);
            // Both completions and observations belong to the attempt they started from.
            if (!current.attemptId().equals(expected.attemptId()) || refresh && !current.equals(expected))
                return null;
            // An older in-flight failure must never overwrite a confirmed result.
            if (!refresh && current.state() == CONFIRMED && state != CONFIRMED)
                return null;
            if (current.state() == state && Objects.equals(current.errorCode(), error))
                return null;
            tx.savePublication(new StudioPublication(version.id(), mode, state, current.requestedBy(),
                    current.requestedAt(), now(), result == null ? null : result.publishedBy(),
                    result == null ? null : result.publishedAt(), error, current.attemptId()));
            audit(tx, actor.principalId(), chain, version, outcomeAction(mode, state), current.attemptId());
            return null;
        });
    }

    private static AuditEvent.Action outcomeAction(StudioPublication.Mode mode, StudioPublication.State state) {
        boolean test = mode == StudioPublication.Mode.TEST;
        return switch (state) {
            case CONFIRMED -> test ? AuditEvent.Action.PUBLICATION_CONFIRMED : AuditEvent.Action.PROMOTION_CONFIRMED;
            case REJECTED -> test ? AuditEvent.Action.PUBLICATION_REJECTED : AuditEvent.Action.PROMOTION_REJECTED;
            case UNKNOWN -> test ? AuditEvent.Action.PUBLICATION_UNKNOWN : AuditEvent.Action.PROMOTION_UNKNOWN;
            case REQUESTED -> throw new IllegalArgumentException("Requested is not an outcome");
        };
    }

    private PublishedArtifact observed(StudioVersion version, StudioPublication.Mode mode) {
        var artifact = publisher.find(version, mode).orElse(null);
        if (artifact != null && (!artifact.assemblyLineId().equals(version.runtimeAssemblyLineId())
                || !artifact.version().equals(version.label()) || artifact.mode() != mode
                || !artifact.artifactHash().equals(version.artifactHash())
                || !artifact.mediaType().equals(version.mediaType())
                || artifact.sizeBytes() != version.source().getBytes(StandardCharsets.UTF_8).length))
            throw new StudioProblem(PUBLICATION_CONFLICT);
        return artifact;
    }

    private VersionState view(StudioStore.Session tx, StudioIdentity actor, StudioVersion version) {
        var rights = requireRead(tx, actor, chain(tx, version.chainId()), Permission.READ);
        var publications = new ArrayList<StudioPublication>();
        for (var mode : StudioPublication.Mode.values()) {
            var publication = tx.publication(version.id(), mode);
            if (publication != null)
                publications.add(publication);
        }
        var actions = new HashSet<String>();
        if (rights.contains(Permission.TEST) && runtime.context().capabilities().contains("CONTROLLED_TEST_VERSION"))
            actions.add("TEST");
        if (rights.contains(Permission.SIMULATE) && runtime.context().capabilities().contains("SIMULATION"))
            actions.add("SIMULATE");
        if (publisher != null) {
            if (rights.contains(Permission.PUBLISH) && publisher.capabilities().contains("PUBLISH_TEST"))
                actions.add("PUBLISH");
            if (rights.contains(Permission.PROMOTE) && publisher.capabilities().contains("PROMOTE_RUN")
                    && publications.stream()
                            .anyMatch(p -> p.mode() == StudioPublication.Mode.TEST && p.state() == CONFIRMED))
                actions.add("PROMOTE");
        }
        return new VersionState(version, publications, actions);
    }

    private void capability(StudioPublication.Mode mode) {
        if (publisher == null || !publisher.capabilities()
                .contains(mode == StudioPublication.Mode.TEST ? "PUBLISH_TEST" : "PROMOTE_RUN"))
            throw new StudioProblem(FORBIDDEN);
    }

    private static Permission permission(StudioPublication.Mode mode) {
        return mode == StudioPublication.Mode.TEST ? Permission.PUBLISH : Permission.PROMOTE;
    }

    private static StudioVersion version(StudioStore.Session tx, UUID id) {
        var version = tx.version(id);
        if (version == null)
            throw new StudioProblem(NOT_FOUND);
        return version;
    }

    private static StudioChain chain(StudioStore.Session tx, UUID id) {
        if (!(resource(tx, id) instanceof StudioChain chain))
            throw new StudioProblem(NOT_FOUND);
        return chain;
    }

    private static StudioDraft draft(StudioStore.Session tx, UUID id) {
        if (!(resource(tx, id) instanceof StudioDraft draft))
            throw new StudioProblem(NOT_FOUND);
        return draft;
    }

    private void audit(StudioStore.Session tx,
                       String actor,
                       StudioChain chain,
                       StudioVersion version,
                       AuditEvent.Action action,
                       UUID attemptId) {
        tx.appendAudit(new AuditEvent(0, UUID.randomUUID(), chain.domainId(), chain.id(), null, ResourceKind.CHAIN,
                chain.id(), chain.version(), actor, action, now(), version.revisionId(), null, null, null,
                version.id(), attemptId));
    }

    private Instant now() {
        return Instant.ofEpochMilli(clock.millis());
    }

    private static String hash(String source) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private record Attempt(StudioVersion version, StudioPublication publication, boolean execute) {}
}
