package io.github.gear4jtest.studio.management;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import io.github.gear4jtest.studio.model.*;
import io.github.gear4jtest.studio.service.StudioProblem;
import io.github.gear4jtest.studio.spi.ScenarioDataPolicy;
import io.github.gear4jtest.studio.spi.StudioRuntime;

import static io.github.gear4jtest.studio.management.ResourceAccess.*;
import static io.github.gear4jtest.studio.service.StudioProblem.Code.*;

/**
 * Reusable fixtures inherit one chain/draft ACL; execution is always explicitly
 * pinned.
 */
final class StudioScenarios {
    private final StudioStore store;
    private final StudioRuntime runtime;
    private final Clock clock;
    private final ScenarioDataPolicy policy;
    private final ControlledTests tests;
    private final StudioRuns runs;
    private final PageCursors cursors;

    StudioScenarios(StudioStore store,
                    StudioRuntime runtime,
                    Clock clock,
                    ScenarioDataPolicy policy,
                    ControlledTests tests,
                    StudioRuns runs) {
        this.store = store;
        this.runtime = runtime;
        this.clock = clock;
        this.policy = policy;
        this.tests = tests;
        this.runs = runs;
        this.cursors = new PageCursors(clock);
    }

    boolean enabled() {
        return policy != null;
    }

    private Instant now() {
        return Instant.ofEpochMilli(clock.millis());
    }

    private void enabledPolicy() {
        if (!enabled())
            throw new StudioProblem(FORBIDDEN);
    }

    private static void limit(int size) {
        if (size < 1 || size > 100)
            throw new StudioProblem(INVALID_REQUEST);
    }

    private static void name(String value) {
        if (value == null || value.isBlank() || value.length() > 160 || value.chars().anyMatch(Character::isISOControl))
            throw new StudioProblem(INVALID_REQUEST);
    }

    private static StudioScenario found(StudioStore.Session tx, UUID id) {
        var value = tx.scenario(id);
        if (value == null)
            throw new StudioProblem(NOT_FOUND);
        return value;
    }

    private static ScenarioRevision revision(StudioStore.Session tx, StudioScenario s, long number) {
        var value = tx.scenarioRevision(s.id(), number);
        if (value == null)
            throw new StudioProblem(NOT_FOUND);
        return value;
    }

    private ManagedResource scope(StudioStore.Session tx, StudioIdentity actor, UUID id, Permission action) {
        var scope = resource(tx, id);
        requireRead(tx, actor, scope, Permission.READ_SCENARIOS);
        require(tx, actor, scope, action);
        if (!(scope instanceof StudioChain) && !(scope instanceof StudioDraft))
            throw new StudioProblem(INVALID_REQUEST);
        return scope;
    }

    private ManagedResource lock(StudioStore.Session tx, StudioIdentity actor, UUID id) {
        var scope = scope(tx, actor, id, Permission.MANAGE_SCENARIOS);
        tx.lockDomain(domain(tx, scope).id());
        return scope(tx, actor, id, Permission.MANAGE_SCENARIOS);
    }

    private void admit(StudioIdentity actor, UUID scope, ScenarioData data) {
        enabledPolicy();
        if (data == null)
            throw new StudioProblem(INVALID_REQUEST);
        try {
            policy.assertSafeToStore(actor, scope, data);
        } catch (RuntimeException denied) {
            throw new StudioProblem(SCENARIO_DATA_NOT_ADMITTED);
        }
    }

    private ScenarioState state(StudioStore.Session tx, StudioIdentity actor, StudioScenario s) {
        var rights = permissions(tx, actor, resource(tx, s.resourceId()));
        var actions = new HashSet<String>();
        if (!s.archived() && enabled()) {
            if (rights.contains(Permission.MANAGE_SCENARIOS))
                actions.addAll(Set.of("EDIT", "ARCHIVE"));
            if (runtime.context().capabilities().contains("SCENARIO_TEST"))
                actions.add("SELECT_TARGET");
        }
        return new ScenarioState(s, revision(tx, s, s.head()), actions);
    }

    ScenarioState create(StudioIdentity actor, UUID scopeId, String name, ScenarioData data) {
        name(name);
        store.read(tx -> scope(tx, actor, scopeId, Permission.MANAGE_SCENARIOS));
        admit(actor, scopeId, data);
        return store.write(tx -> {
            var scope = lock(tx, actor, scopeId);
            var chainId = scope instanceof StudioChain c ? c.id() : ((StudioDraft) scope).chainId();
            var time = now();
            var s = new StudioScenario(UUID.randomUUID(), scopeId, chainId, name, actor.subject(), 1, 1, false, time,
                    time);
            var r = new ScenarioRevision(s.id(), UUID.randomUUID(), 1, actor.subject(), time, data);
            tx.insertScenario(s);
            tx.appendScenarioRevision(r);
            audit(tx, actor, scope, AuditEvent.Action.SCENARIO_CREATED, r.reference(), null, null, null);
            return state(tx, actor, s);
        });
    }

    ScenarioState save(StudioIdentity actor, UUID id, long expected, String name, ScenarioData data) {
        name(name);
        var original = store.read(tx -> {
            var s = found(tx, id);
            scope(tx, actor, s.resourceId(), Permission.MANAGE_SCENARIOS);
            return s;
        });
        admit(actor, original.resourceId(), data);
        return store.write(tx -> {
            var scope = lock(tx, actor, original.resourceId());
            var old = found(tx, id);
            mutable(old, expected);
            var s = new StudioScenario(id, old.resourceId(), old.chainId(), name, old.owner(), old.version() + 1,
                    old.head() + 1, false, old.createdAt(), now());
            var r = new ScenarioRevision(id, UUID.randomUUID(), s.head(), actor.subject(), now(), data);
            tx.updateScenario(s, expected);
            tx.appendScenarioRevision(r);
            audit(tx, actor, scope, AuditEvent.Action.SCENARIO_REVISED, r.reference(), null, null, null);
            return state(tx, actor, s);
        });
    }

    ScenarioState archive(StudioIdentity actor, UUID id, long expected) {
        enabledPolicy();
        return store.write(tx -> {
            var old = found(tx, id);
            var scope = lock(tx, actor, old.resourceId());
            old = found(tx, id);
            mutable(old, expected);
            var s = new StudioScenario(id, old.resourceId(), old.chainId(), old.name(), old.owner(), old.version() + 1,
                    old.head(), true, old.createdAt(), now());
            tx.updateScenario(s, expected);
            audit(tx, actor, scope, AuditEvent.Action.SCENARIO_ARCHIVED, revision(tx, s, s.head()).reference(), null,
                  null, null);
            return state(tx, actor, s);
        });
    }

    private static void mutable(StudioScenario s, long expected) {
        if (expected < 1 || s.version() != expected)
            throw new StudioProblem(CONFLICT);
        if (s.archived())
            throw new StudioProblem(FORBIDDEN);
    }

    ScenarioState get(StudioIdentity actor, UUID id) {
        return store.read(tx -> {
            var s = found(tx, id);
            scope(tx, actor, s.resourceId(), Permission.READ_SCENARIOS);
            return state(tx, actor, s);
        });
    }

    StudioPage<ScenarioState> list(StudioIdentity actor, UUID id, String after, int size) {
        limit(size);
        String key = "scenarios:" + id;
        String cursor = cursors.decode(after, actor, key);
        return store.read(tx -> {
            scope(tx, actor, id, Permission.READ_SCENARIOS);
            var rows = tx.scenarios(id, cursor, size + 1);
            var items = rows.stream().limit(size).map(s -> state(tx, actor, s)).toList();
            return new StudioPage<>(items, rows.size() <= size ? null
                    : cursors.encode(items.get(items.size() - 1).scenario().id().toString(), actor, key));
        });
    }

    StudioPage<ScenarioRevision> revisions(StudioIdentity actor, UUID id, String after, int size) {
        limit(size);
        String key = "scenario-revisions:" + id;
        String position = cursors.decode(after, actor, key);
        long cursor = position == null ? 0 : Long.parseLong(position);
        return store.read(tx -> {
            var s = found(tx, id);
            scope(tx, actor, s.resourceId(), Permission.READ_SCENARIOS);
            var rows = tx.scenarioRevisions(id, cursor, size + 1);
            var items = rows.stream().limit(size).toList();
            return new StudioPage<>(items, rows.size() <= size ? null
                    : cursors.encode(Long.toString(items.get(items.size() - 1).number()), actor, key));
        });
    }

    TestSubmission test(StudioIdentity actor, UUID id, long number, ScenarioTarget target, UUID requestId) {
        enabledPolicy();
        if (!runtime.context().capabilities().contains("SCENARIO_TEST"))
            throw new StudioProblem(FORBIDDEN);
        if (target == null || requestId == null)
            throw new StudioProblem(INVALID_REQUEST);
        var selected = store.read(tx -> selection(tx, actor, id, number, target));
        admit(actor, selected.scenario().resourceId(), selected.revision().data());
        String input;
        ResolvedFixture fixture;
        try {
            var declared = selected.revision().data().input();
            if (declared.kind() == ScenarioInput.Kind.SYNTHETIC_TEXT) {
                input = declared.value();
                fixture = null;
            } else {
                var resolved = policy.resolveFixture(actor, selected.scenario().resourceId(), declared);
                input = resolved.value();
                fixture = new ResolvedFixture(declared.value(), declared.fixtureVersion(), resolved.fingerprint());
            }
        } catch (RuntimeException denied) {
            throw new StudioProblem(SCENARIO_DATA_NOT_ADMITTED);
        }
        if (input == null || input.length() > 8192)
            throw new StudioProblem(SCENARIO_DATA_NOT_ADMITTED);
        UUID content = UUID.nameUUIDFromBytes(((selected.version() == null ? selected.draft().revisionId()
                : selected.version().id())
                + ":" + selected.revision().id() + ":" + fixture).getBytes(StandardCharsets.UTF_8));
        UUID runId = UUID.randomUUID();
        var reference = selected.revision().reference();
        return tests.execute(actor, selected.targetScope().id(), content, requestId, input,
                             () -> runs.execute(runId, observer -> {
                                 var data = selected.revision().data();
                                 var result = selected.version() == null
                                         ? runtime.executeScenario(selected.draft(), input, requestId, actor.subject(),
                                                                   data, observer)
                                         : runtime.executeVersionScenario(selected.version(), input, requestId,
                                                                          actor.subject(), data, observer);
                                 var check = result.scenarioCheck() == null
                                         ? new ScenarioCheck(ScenarioCheck.Status.NOT_EVALUATED, java.util.List.of())
                                         : result.scenarioCheck();
                                 return result.withScenario(reference, check).withFixture(fixture);
                             }), () -> store.write(tx -> {
                                 tx.lockDomain(domain(tx, resource(tx, selected.scenario().resourceId())).id());
                                 var current = selection(tx, actor, id, number, target);
                                 audit(tx, actor, current.targetScope(), AuditEvent.Action.TEST_REQUESTED, reference,
                                       current.draft().revisionId(), requestId,
                                       current.version() == null ? null : current.version().id());
                                 runs.begin(tx, runId, actor, current.draft(), current.version(), requestId, reference,
                                            fixture);
                                 return null;
                             }), selected.scenario().resourceId());
    }

    private Selection selection(StudioStore.Session tx,
                                StudioIdentity actor,
                                UUID id,
                                long number,
                                ScenarioTarget target) {
        var s = found(tx, id);
        var fixtureScope = scope(tx, actor, s.resourceId(), Permission.READ_SCENARIOS);
        if (s.archived())
            throw new StudioProblem(FORBIDDEN);
        var r = revision(tx, s, number);
        StudioVersion version = target.versionId() == null ? null : tx.version(target.versionId());
        if (target.versionId() != null && version == null)
            throw new StudioProblem(NOT_FOUND);
        var targetScope = resource(tx, version == null ? target.draftId() : version.chainId());
        requireRead(tx, actor, targetScope, Permission.TEST);
        if (version == null && !(targetScope instanceof StudioDraft))
            throw new StudioProblem(NOT_FOUND);
        UUID chainId = version == null ? ((StudioDraft) targetScope).chainId() : version.chainId();
        if (!chainId.equals(s.chainId())
                || fixtureScope instanceof StudioDraft && !fixtureScope.id().equals(targetScope.id()))
            throw new StudioProblem(SCENARIO_TARGET_MISMATCH);
        var draft = tx.revision(version == null ? target.draftId() : version.draftId(),
                                version == null ? target.revision() : version.revision());
        if (draft == null)
            throw new StudioProblem(NOT_FOUND);
        draft.requireDefinition();
        return new Selection(s, r, draft, version, targetScope);
    }

    private void audit(StudioStore.Session tx,
                       StudioIdentity actor,
                       ManagedResource scope,
                       AuditEvent.Action action,
                       ScenarioRef reference,
                       UUID revisionId,
                       UUID requestId,
                       UUID versionId) {
        var chainId = scope instanceof StudioChain c ? c.id() : ((StudioDraft) scope).chainId();
        tx.appendAudit(new AuditEvent(0, UUID.randomUUID(), domain(tx, scope).id(), chainId,
                scope instanceof StudioDraft ? scope.id() : null, scope.kind(), scope.id(), scope.version(),
                actor.subject(), action,
                now(), revisionId, requestId, null, null, versionId, null, reference));
    }

    private record Selection(StudioScenario scenario,
                             ScenarioRevision revision,
                             DraftRevision draft,
                             StudioVersion version,
                             ManagedResource targetScope) {}
}
