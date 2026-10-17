package io.github.gear4jtest.studio.management;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

import io.github.gear4jtest.studio.model.ChainDefinition;
import io.github.gear4jtest.studio.model.DefinitionComparison;
import io.github.gear4jtest.studio.model.DefinitionDiff;
import io.github.gear4jtest.studio.model.Diagnostic;
import io.github.gear4jtest.studio.model.DraftRevision;
import io.github.gear4jtest.studio.model.OperationDescriptor;
import io.github.gear4jtest.studio.model.RuntimeContext;
import io.github.gear4jtest.studio.model.TestSubmission;
import io.github.gear4jtest.studio.model.ValidationReport;
import io.github.gear4jtest.studio.service.StudioProblem;
import io.github.gear4jtest.studio.spi.StudioRuntime;

import static io.github.gear4jtest.studio.management.ResourceAccess.*;
import static io.github.gear4jtest.studio.service.StudioProblem.Code.*;

/**
 * Management use cases. Actor resolution and authorization happen again on
 * every public request.
 */
public final class StudioManagementService {
    private final StudioStore store;
    private final StudioRuntime runtime;
    private final CurrentActorProvider actors;
    private final Predicate<StudioIdentity> domainCreation;
    private final Clock clock;
    private final PageCursors cursors;
    private static final int SCAN_BUDGET = 500;
    private final ControlledTests tests = new ControlledTests();

    public StudioManagementService(StudioStore store,
                                   StudioRuntime runtime,
                                   CurrentActorProvider actors,
                                   Predicate<StudioIdentity> domainCreation) {
        this(store, runtime, actors, domainCreation, Clock.systemUTC());
    }

    public StudioManagementService(StudioStore store,
                                   StudioRuntime runtime,
                                   CurrentActorProvider actors,
                                   Predicate<StudioIdentity> domainCreation,
                                   Clock clock) {
        this.store = Objects.requireNonNull(store);
        this.runtime = Objects.requireNonNull(runtime);
        this.actors = Objects.requireNonNull(actors);
        this.domainCreation = Objects.requireNonNull(domainCreation);
        this.clock = Objects.requireNonNull(clock);
        this.cursors = new PageCursors(clock);
        store.bindContext(runtime.context().id());
    }

    public StudioIdentity identity() {
        var actor = actors.currentActor();
        if (actor == null)
            throw new StudioProblem(UNAUTHENTICATED);
        return actor;
    }

    public RuntimeContext context() {
        identity();
        return runtime.context();
    }

    public List<OperationDescriptor> catalog() {
        identity();
        return runtime.catalog();
    }

    public boolean canCreateDomain() {
        var actor = identity();
        return actor.administrator() || domainCreation.test(actor);
    }

    private Instant now() {
        return Instant.ofEpochMilli(clock.millis());
    }

    private static String text(String value, int max, boolean required) {
        if (value == null || value.length() > max || required && value.isBlank()
                || value.chars().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\t'))
            throw new StudioProblem(INVALID_REQUEST);
        return value;
    }

    private static String key(String key) {
        if (key == null || !key.matches("[a-z][a-z0-9-]{0,63}"))
            throw new StudioProblem(INVALID_REQUEST);
        return key;
    }

    private static int limit(int value) {
        if (value < 1 || value > 100)
            throw new StudioProblem(INVALID_REQUEST);
        return value;
    }

    private static void expected(ManagedResource value, long expected) {
        if (expected < 1 || value.version() != expected)
            throw new StudioProblem(CONFLICT);
    }

    private ManagedResource locked(StudioStore.Session tx, UUID id) {
        var initial = ResourceAccess.resource(tx, id);
        tx.lockDomain(domain(tx, initial).id());
        return ResourceAccess.resource(tx, id);
    }

    public ResourceView<StudioDomain> createDomain(String key, String name, String description) {
        var actor = identity();
        if (!actor.administrator() && !domainCreation.test(actor))
            throw new StudioProblem(FORBIDDEN);
        key(key);
        text(name, 160, true);
        text(description, 2048, false);
        return store.write(tx -> {
            var time = now();
            var value = new StudioDomain(UUID.randomUUID(), key, name, description, actor.subject(), 1, false, time,
                    time);
            tx.insert(value);
            audit(tx, actor, value, AuditEvent.Action.DOMAIN_CREATED, null, null, null, null);
            return new ResourceView<>(value, permissions(tx, actor, value));
        });
    }

    public ResourceView<StudioChain> createChain(UUID domainId, String key, String name, String description) {
        return createChain(domainId, key, name, description, "chain_" + UUID.randomUUID().toString().replace("-", ""));
    }

    public ResourceView<StudioChain> createChain(UUID domainId,
                                                 String key,
                                                 String name,
                                                 String description,
                                                 String runtimeAssemblyLineId) {
        var actor = identity();
        key(key);
        text(name, 160, true);
        text(description, 2048, false);
        if (runtimeAssemblyLineId == null || !runtimeAssemblyLineId.matches("[a-zA-Z_][a-zA-Z0-9_]{0,63}")
                || javax.lang.model.SourceVersion.isKeyword(runtimeAssemblyLineId))
            throw new StudioProblem(INVALID_REQUEST);
        return store.write(tx -> {
            var parent = typed(locked(tx, domainId), StudioDomain.class);
            requireRead(tx, actor, parent, Permission.CREATE_CHAIN);
            var time = now();
            var value = new StudioChain(UUID.randomUUID(), domainId, key, runtimeAssemblyLineId, name, description,
                    actor.subject(), 1, true,
                    false, time, time);
            tx.insert(value);
            audit(tx, actor, value, AuditEvent.Action.CHAIN_CREATED, null, null, null, null);
            return new ResourceView<>(value, permissions(tx, actor, value));
        });
    }

    public DraftState createDraft(UUID chainId, String name, ChainDefinition definition) {
        return createDraft(chainId, name, definition, DraftRevision.Origin.STUDIO, null);
    }

    public DraftState importDraft(UUID chainId, String name, String source) {
        // Check the scope before asking an adapter to parse an untrusted source.
        authorize(chainId, Permission.CREATE_DRAFT);
        return createDraft(chainId, name, runtime.importDefinition(source, "application/xml"),
                           DraftRevision.Origin.XML_IMPORT, null);
    }

    private DraftState createDraft(UUID chainId,
                                   String name,
                                   ChainDefinition definition,
                                   DraftRevision.Origin origin,
                                   UUID base) {
        var actor = identity();
        text(name, 160, true);
        return store.write(tx -> {
            var parent = typed(locked(tx, chainId), StudioChain.class);
            requireRead(tx, actor, parent, Permission.CREATE_DRAFT);
            runtime.assertSafeToStore(definition);
            matchingIdentity(parent, definition);
            return insertDraft(tx, actor, parent, name, definition, origin, base);
        });
    }

    private DraftState insertDraft(StudioStore.Session tx,
                                   StudioIdentity actor,
                                   StudioChain chain,
                                   String name,
                                   ChainDefinition definition,
                                   DraftRevision.Origin origin,
                                   UUID base) {
        var time = now();
        var draft = new StudioDraft(UUID.randomUUID(), chain.id(), name, actor.subject(), 1, false, false, 1, origin,
                base, time, time);
        var revision = new DraftRevision(draft.id(), UUID.randomUUID(), 1, actor.subject(), time, origin, definition);
        tx.insert(draft);
        tx.appendRevision(revision);
        audit(tx, actor, draft, AuditEvent.Action.DRAFT_CREATED, revision.revisionId(), null, null, null);
        return new DraftState(draft, revision, permissions(tx, actor, draft));
    }

    public DraftState forkDraft(UUID sourceId, long number, String name) {
        var actor = identity();
        text(name, 160, true);
        return store.write(tx -> {
            var source = typed(locked(tx, sourceId), StudioDraft.class);
            requireRead(tx, actor, source, Permission.READ);
            var parent = chain(tx, source);
            requireRead(tx, actor, parent, Permission.CREATE_DRAFT);
            var revision = revision(tx, source.id(), number);
            runtime.assertSafeToStore(revision.definition());
            return insertDraft(tx, actor, parent, name, revision.definition(), revision.origin(),
                               revision.revisionId());
        });
    }

    public DraftState saveDraft(UUID id, long expectedVersion, ChainDefinition definition) {
        var actor = identity();
        return store.write(tx -> {
            var old = typed(locked(tx, id), StudioDraft.class);
            requireRead(tx, actor, old, Permission.EDIT);
            expected(old, expectedVersion);
            runtime.assertSafeToStore(definition);
            matchingIdentity(chain(tx, old), definition);
            var time = now();
            var draft = new StudioDraft(old.id(), old.chainId(), old.name(), old.owner(), old.version() + 1,
                    old.inheritAccess(), false, old.head() + 1, old.origin(), old.baseRevisionId(), old.createdAt(),
                    time);
            var revision = new DraftRevision(id, UUID.randomUUID(), draft.head(), actor.subject(), time, old.origin(),
                    definition);
            tx.update(draft, expectedVersion);
            tx.appendRevision(revision);
            audit(tx, actor, draft, AuditEvent.Action.REVISION_SAVED, revision.revisionId(), null, null, null);
            return new DraftState(draft, revision, permissions(tx, actor, draft));
        });
    }

    public ResourceView<ManagedResource> resource(UUID id) {
        var actor = identity();
        return store.read(tx -> {
            var value = ResourceAccess.resource(tx, id);
            return new ResourceView<>(value, requireRead(tx, actor, value, Permission.READ));
        });
    }

    public DraftState draft(UUID id) {
        var actor = identity();
        return store.read(tx -> {
            var value = typed(ResourceAccess.resource(tx, id), StudioDraft.class);
            var permissions = requireRead(tx, actor, value, Permission.READ);
            return new DraftState(value, revision(tx, id, value.head()), permissions);
        });
    }

    public StudioPage<DraftRevision> revisions(UUID id, long after, int size) {
        var actor = identity();
        limit(size);
        if (after < 0)
            throw new StudioProblem(INVALID_REQUEST);
        return store.read(tx -> {
            var draft = typed(ResourceAccess.resource(tx, id), StudioDraft.class);
            requireRead(tx, actor, draft, Permission.READ);
            var values = tx.revisions(id, after, size + 1);
            boolean more = values.size() > size;
            var page = more ? values.subList(0, size) : values;
            return new StudioPage<>(page, more ? Long.toString(page.get(page.size() - 1).number()) : null);
        });
    }

    public StudioPage<DiscoveryItem> list(ResourceKind kind, UUID parent, String after, int size) {
        var actor = identity();
        limit(size);
        String scope = kind + ":" + parent;
        String position = cursors.decode(after, actor, scope);
        return store.read(tx -> {
            var result = new ArrayList<DiscoveryItem>();
            String cursor = position;
            int scanned = 0;
            boolean exhausted = false;
            while (result.size() <= size && scanned < SCAN_BUDGET) {
                List<? extends ManagedResource> batch = switch (kind) {
                    case DOMAIN -> tx.domains(cursor, 100);
                    case CHAIN -> tx.chains(parent, cursor, 100);
                    case DRAFT -> tx.drafts(parent, cursor, 100);
                };
                for (var value : batch) {
                    cursor = value.id().toString();
                    scanned++;
                    var allowed = permissions(tx, actor, value);
                    if (allowed.contains(Permission.DISCOVER)) {
                        UUID parentId = value instanceof StudioChain c ? c.domainId()
                                : value instanceof StudioDraft d ? d.chainId() : null;
                        result.add(new DiscoveryItem(value.id(), value.kind(), parentId, value.name(),
                                allowed.contains(Permission.READ)));
                        if (result.size() > size)
                            break;
                    }
                }
                if (batch.size() < 100) {
                    exhausted = true;
                    break;
                }
            }
            boolean lookahead = result.size() > size;
            var page = lookahead ? result.subList(0, size) : result;
            String next = lookahead ? page.get(page.size() - 1).id().toString() : !exhausted ? cursor : null;
            return new StudioPage<>(page, next == null ? null : cursors.encode(next, actor, scope));
        });
    }

    public AccessState access(UUID id) {
        var actor = identity();
        return store.read(tx -> {
            var value = ResourceAccess.resource(tx, id);
            requireRead(tx, actor, value, Permission.MANAGE_ACCESS);
            return new AccessState(value.version(), new AccessPolicy(value.inheritAccess(), tx.grants(id)),
                    permissions(tx, actor, value), AccessDelegation.administrator(tx, actor, value),
                    AccessDelegation.grantable(tx, actor, value));
        });
    }

    public ResourceView<ManagedResource> setAccess(UUID id, long expectedVersion, AccessPolicy policy) {
        var actor = identity();
        Objects.requireNonNull(policy);
        return store.write(tx -> {
            var old = locked(tx, id);
            requireRead(tx, actor, old, Permission.MANAGE_ACCESS);
            expected(old, expectedVersion);
            if (old instanceof StudioDomain && policy.inheritAccess())
                throw new StudioProblem(INVALID_REQUEST);
            var before = new AccessPolicy(old.inheritAccess(), tx.grants(id));
            AccessDelegation.check(tx, actor, old, before, policy);
            var value = updated(old, old.name(), description(old), policy.inheritAccess(), false);
            tx.update(value, expectedVersion);
            tx.replaceGrants(id, policy.grants());
            audit(tx, actor, value, AuditEvent.Action.ACCESS_CHANGED, null, null, before, policy);
            return new ResourceView<>(value, permissions(tx, actor, value));
        });
    }

    public ResourceView<ManagedResource> updateMetadata(UUID id,
                                                        long expectedVersion,
                                                        String name,
                                                        String description) {
        var actor = identity();
        text(name, 160, true);
        text(description, 2048, false);
        return store.write(tx -> {
            var old = locked(tx, id);
            requireRead(tx, actor, old, Permission.EDIT);
            expected(old, expectedVersion);
            if (old instanceof StudioDraft && !description.isEmpty())
                throw new StudioProblem(INVALID_REQUEST);
            var value = updated(old, name, description, old.inheritAccess(), false);
            tx.update(value, expectedVersion);
            audit(tx, actor, value, AuditEvent.Action.METADATA_UPDATED, null, null, null, null);
            return new ResourceView<>(value, permissions(tx, actor, value));
        });
    }

    public ResourceView<ManagedResource> archive(UUID id, long expectedVersion) {
        var actor = identity();
        return store.write(tx -> {
            var old = locked(tx, id);
            requireRead(tx, actor, old, Permission.ARCHIVE);
            expected(old, expectedVersion);
            var value = updated(old, old.name(), description(old), old.inheritAccess(), true);
            tx.update(value, expectedVersion);
            var action = switch (value.kind()) {
                case DOMAIN -> AuditEvent.Action.DOMAIN_ARCHIVED;
                case CHAIN -> AuditEvent.Action.CHAIN_ARCHIVED;
                case DRAFT -> AuditEvent.Action.DRAFT_ABANDONED;
            };
            audit(tx, actor, value, action, null, null, null, null);
            return new ResourceView<>(value, permissions(tx, actor, value));
        });
    }

    private static String description(ManagedResource value) {
        return value instanceof StudioDomain d ? d.description()
                : value instanceof StudioChain c ? c.description() : "";
    }

    private ManagedResource updated(ManagedResource old,
                                    String name,
                                    String description,
                                    boolean inherit,
                                    boolean archive) {
        var time = now();
        if (old instanceof StudioDomain d)
            return new StudioDomain(d.id(), d.key(), name, description, d.owner(), d.version() + 1,
                    archive || d.archived(), d.createdAt(), time);
        if (old instanceof StudioChain c)
            return new StudioChain(c.id(), c.domainId(), c.key(), c.runtimeAssemblyLineId(), name, description,
                    c.owner(), c.version() + 1,
                    inherit, archive || c.archived(), c.createdAt(), time);
        var d = (StudioDraft) old;
        return new StudioDraft(d.id(), d.chainId(), name, d.owner(), d.version() + 1, inherit, archive || d.abandoned(),
                d.head(), d.origin(), d.baseRevisionId(), d.createdAt(), time);
    }

    public ValidationReport analyze(UUID scope, ChainDefinition definition) {
        authorize(scope, Permission.VALIDATE);
        capability("VALIDATE");
        if (!definition.id().equals(runtimeId(scope)))
            return new ValidationReport(runtime.context().catalogFingerprint(), List.of(Diagnostic.error(
                                                                                                         "CHAIN_ID_MISMATCH",
                                                                                                         "/id",
                                                                                                         "L’identifiant Gear4J est immuable pour cette chaîne.")));
        return runtime.analyze(definition);
    }

    public ValidationReport validate(UUID scope, ChainDefinition definition) {
        authorize(scope, Permission.VALIDATE);
        capability("VALIDATE");
        if (!definition.id().equals(runtimeId(scope)))
            return new ValidationReport(runtime.context().catalogFingerprint(), List.of(Diagnostic.error(
                                                                                                         "CHAIN_ID_MISMATCH",
                                                                                                         "/id",
                                                                                                         "L’identifiant Gear4J doit correspondre à celui de la chaîne Studio.")));
        return runtime.validate(definition);
    }

    private static void matchingIdentity(StudioChain chain, ChainDefinition definition) {
        if (!chain.runtimeAssemblyLineId().equals(definition.id()))
            throw new StudioProblem(INVALID_DEFINITION);
    }

    private String runtimeId(UUID scope) {
        return store.read(tx -> {
            var resource = ResourceAccess.resource(tx, scope);
            var chain = resource instanceof StudioDraft draft ? chain(tx, draft) : typed(resource, StudioChain.class);
            return chain.runtimeAssemblyLineId();
        });
    }

    /**
     * Parses without persisting; applying and saving are separate explicit
     * authoring actions.
     */
    public ChainDefinition previewXml(UUID scope, String source) {
        authorize(scope, Permission.EDIT);
        var definition = runtime.importDefinition(source, "application/xml");
        if (!definition.id().equals(runtimeId(scope)))
            throw new StudioProblem(INVALID_DEFINITION);
        runtime.assertSafeToStore(definition);
        return definition;
    }

    /**
     * Compares local work with an immutable revision, under the draft's current
     * read permissions.
     */
    public DefinitionComparison compare(UUID draftId, long baseNumber, ChainDefinition proposed) {
        var actor = identity();
        var base = store.read(tx -> {
            var draft = typed(ResourceAccess.resource(tx, draftId), StudioDraft.class);
            requireRead(tx, actor, draft, Permission.READ);
            matchingIdentity(chain(tx, draft), proposed);
            return revision(tx, draftId, baseNumber);
        });
        return new DefinitionComparison(base.revisionId(), DefinitionDiff.between(base.definition(), proposed),
                optionalXml(base.definition()), optionalXml(proposed));
    }

    public DefinitionComparison compareRevisions(UUID draftId, long fromNumber, long toNumber) {
        var actor = identity();
        var pair = store.read(tx -> {
            var draft = typed(ResourceAccess.resource(tx, draftId), StudioDraft.class);
            requireRead(tx, actor, draft, Permission.READ);
            return List.of(revision(tx, draftId, fromNumber), revision(tx, draftId, toNumber));
        });
        return new DefinitionComparison(pair.get(0).revisionId(),
                DefinitionDiff.between(pair.get(0).definition(), pair.get(1).definition()),
                optionalXml(pair.get(0).definition()), optionalXml(pair.get(1).definition()));
    }

    private String optionalXml(ChainDefinition definition) {
        try {
            return runtime.exportDefinition(definition, "application/xml");
        } catch (StudioProblem | SecurityException unavailable) {
            return null;
        }
    }

    public String export(UUID scope, ChainDefinition definition) {
        authorize(scope, Permission.READ);
        if (!definition.id().equals(runtimeId(scope)))
            throw new StudioProblem(INVALID_DEFINITION);
        return runtime.exportDefinition(definition, "application/xml");
    }

    private void authorize(UUID scope, Permission permission) {
        var actor = identity();
        store.read(tx -> {
            requireRead(tx, actor, ResourceAccess.resource(tx, scope), permission);
            return null;
        });
    }

    private void capability(String capability) {
        if (!runtime.context().capabilities().contains(capability))
            throw new StudioProblem(FORBIDDEN);
    }

    public TestSubmission test(UUID id, long number, UUID requestId, String input) {
        var actor = identity();
        capability("CONTROLLED_TEST");
        var revision = store.read(tx -> {
            var draft = typed(ResourceAccess.resource(tx, id), StudioDraft.class);
            requireRead(tx, actor, draft, Permission.TEST);
            return revision(tx, id, number);
        });
        return tests.execute(actor, revision, requestId, input, runtime, () -> store.write(tx -> {
            var draft = typed(locked(tx, id), StudioDraft.class);
            requireRead(tx, actor, draft, Permission.TEST);
            audit(tx, actor, draft, AuditEvent.Action.TEST_REQUESTED, revision.revisionId(), requestId, null, null);
            return null;
        }));
    }

    public TestSubmission testRequest(UUID requestId) {
        var actor = identity();
        var id = tests.draftId(actor.subject(), requestId);
        store.read(tx -> {
            requireRead(tx, actor, ResourceAccess.resource(tx, id), Permission.READ);
            return null;
        });
        return tests.status(actor.subject(), requestId);
    }

    public StudioPage<AuditEvent> audit(UUID scope, String after, int size) {
        var actor = identity();
        limit(size);
        String cursorScope = "audit:" + scope;
        String position = cursors.decode(after, actor, cursorScope);
        return store.read(tx -> {
            var target = ResourceAccess.resource(tx, scope);
            require(tx, actor, target, Permission.READ_AUDIT);
            var domain = domain(tx, target);
            UUID chainId = target instanceof StudioChain c ? c.id()
                    : target instanceof StudioDraft d ? d.chainId() : null;
            UUID draftId = target instanceof StudioDraft d ? d.id() : null;
            var result = new ArrayList<AuditEvent>();
            long cursor = position == null ? 0 : Long.parseLong(position);
            int scanned = 0;
            boolean exhausted = false;
            while (result.size() <= size && scanned < SCAN_BUDGET) {
                var batch = tx.audit(domain.id(), chainId, draftId, cursor, 100);
                for (var event : batch) {
                    cursor = event.sequence();
                    scanned++;
                    var value = ResourceAccess.resource(tx, event.resourceId());
                    var rights = permissions(tx, actor, value);
                    if (rights.contains(Permission.DISCOVER) && rights.contains(Permission.READ_AUDIT))
                        result.add(event);
                    if (result.size() > size)
                        break;
                }
                if (batch.size() < 100) {
                    exhausted = true;
                    break;
                }
            }
            boolean lookahead = result.size() > size;
            var page = lookahead ? result.subList(0, size) : result;
            String next = lookahead ? Long.toString(page.get(page.size() - 1).sequence())
                    : !exhausted ? Long.toString(cursor) : null;
            return new StudioPage<>(page, next == null ? null : cursors.encode(next, actor, cursorScope));
        });
    }

    private void audit(StudioStore.Session tx,
                       StudioIdentity actor,
                       ManagedResource value,
                       AuditEvent.Action action,
                       UUID revisionId,
                       UUID requestId,
                       AccessPolicy before,
                       AccessPolicy after) {
        var domain = domain(tx, value);
        UUID chainId = value instanceof StudioChain c ? c.id() : value instanceof StudioDraft d ? d.chainId() : null;
        UUID draftId = value instanceof StudioDraft d ? d.id() : null;
        tx.appendAudit(new AuditEvent(0, UUID.randomUUID(), domain.id(), chainId, draftId, value.kind(), value.id(),
                value.version(),
                actor.subject(), action, now(), revisionId, requestId, before, after));
    }

    private static <T extends ManagedResource> T typed(ManagedResource resource, Class<T> type) {
        if (!type.isInstance(resource))
            throw new StudioProblem(NOT_FOUND);
        return type.cast(resource);
    }

    private static DraftRevision revision(StudioStore.Session tx, UUID id, long number) {
        if (number < 1)
            throw new StudioProblem(NOT_FOUND);
        var revision = tx.revision(id, number);
        if (revision == null)
            throw new StudioProblem(NOT_FOUND);
        return revision;
    }
}
