package io.github.gear4jtest.studio.management;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.gear4jtest.jdbc.persistence.Gear4jDatabaseDialect;
import io.github.gear4jtest.studio.jdbc.JdbcStudioStore;
import io.github.gear4jtest.studio.jdbc.StudioJdbcSchema;
import io.github.gear4jtest.studio.model.*;
import io.github.gear4jtest.studio.service.StudioProblem;
import io.github.gear4jtest.studio.spi.StudioRuntime;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static io.github.gear4jtest.studio.management.Permission.*;
import static org.assertj.core.api.Assertions.*;

class StudioManagementTest {
    private static final ChainDefinition EMPTY = new ChainDefinition(1, "sample", "java.lang.String",
            "java.lang.String", List.of());
    private final JdbcDataSource source = new JdbcDataSource();
    private final JdbcStudioStore store;
    private final StubRuntime runtime = new StubRuntime();

    StudioManagementTest() {
        source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000");
        StudioJdbcSchema.migrate(source, Gear4jDatabaseDialect.H2);
        store = new JdbcStudioStore(source);
    }

    @AfterEach
    void close() throws Exception {
        try (var c = source.getConnection(); var s = c.createStatement()) {
            s.execute("SHUTDOWN");
        }
    }

    private StudioManagementService as(String subject, String... groups) {
        return new StudioManagementService(store, runtime, () -> new StudioIdentity(subject, Set.of(groups), false),
                a -> true);
    }

    private AccessGrant grant(AccessGrant.SubjectType type, String subject, Permission... permissions) {
        return new AccessGrant(type, subject, Set.of(permissions));
    }

    private DraftState draft(StudioManagementService alice) {
        var domain = alice.createDomain("catalog", "Catalogue", "Domaine").resource();
        var chain = alice.createChain(domain.id(), "vehicles", "Véhicules", "Chaîne").resource();
        return alice.createDraft(chain.id(), "En cours", EMPTY);
    }

    @Test
    void privateDraftsDiscoveryAndExplicitSharingAreDistinct() {
        // Given
        var alice = as("alice");
        var bob = as("bob", "reviewers");
        var draft = draft(alice);
        var chain = alice.resource(draft.draft().chainId()).resource();
        alice.setAccess(chain.id(), chain.version(),
                        new AccessPolicy(true, List.of(grant(AccessGrant.SubjectType.GROUP, "reviewers", DISCOVER))));
        // When / Then: discovery does not expose definitions or ownership.
        assertThat(bob.list(ResourceKind.CHAIN, null, null, 10).items()).singleElement()
                .satisfies(item -> assertThat(item.readable()).isFalse());
        assertThatThrownBy(() -> bob.resource(chain.id())).hasMessage("FORBIDDEN");
        assertThat(bob.list(ResourceKind.DRAFT, null, null, 10).items()).isEmpty();
        assertThatThrownBy(() -> bob.draft(draft.draft().id())).hasMessage("NOT_FOUND");
        alice.setAccess(draft.draft().id(), 1,
                        new AccessPolicy(false, List.of(grant(AccessGrant.SubjectType.USER, "bob", READ, EDIT))));
        var visible = bob.draft(draft.draft().id());
        assertThat(visible.permissions()).contains(READ, EDIT, DISCOVER).doesNotContain(TEST, MANAGE_ACCESS);
        assertThatThrownBy(() -> bob.test(visible.draft().id(), 1, UUID.randomUUID(), "input")).hasMessage("FORBIDDEN");
        assertThatThrownBy(() -> bob.setAccess(visible.draft().id(), visible.draft().version(),
                                               new AccessPolicy(true, List.of())))
                .hasMessage("FORBIDDEN");
        var saved = bob.saveDraft(visible.draft().id(), visible.draft().version(), EMPTY);
        assertThat(saved.revision().author()).isEqualTo("bob");
        alice.setAccess(saved.draft().id(), saved.draft().version(), new AccessPolicy(false, List.of()));
        assertThatThrownBy(() -> bob.draft(saved.draft().id())).hasMessage("NOT_FOUND");
    }

    @Test
    void draftRevisionLineageMetadataAndArchivePreserveHistory() {
        // Given
        var alice = as("alice");
        var initial = draft(alice);
        var id = initial.draft().id();
        // When
        assertThat(alice.validate(id, EMPTY).valid()).isFalse();
        var saved = alice
                .saveDraft(id, 1, new ChainDefinition(1, "changed", "java.lang.String", "java.lang.String", List.of()));
        var fork = alice.forkDraft(id, 1, "Alternative");
        var renamed = alice.updateMetadata(id, saved.draft().version(), "Revue", "");
        alice.archive(id, renamed.resource().version());
        // Then
        assertThat(fork.draft().baseRevisionId()).isEqualTo(initial.revision().revisionId());
        assertThat(fork.revision().definition()).isEqualTo(EMPTY);
        assertThat(alice.revisions(id, 0, 1).nextCursor()).isEqualTo("1");
        assertThat(alice.revisions(id, 1, 10).items()).extracting(DraftRevision::revisionId)
                .containsExactly(saved.revision().revisionId());
        assertThat(alice.draft(id).draft().abandoned()).isTrue();
        assertThatThrownBy(() -> alice.saveDraft(id, 4, EMPTY)).hasMessage("FORBIDDEN");
        assertThatThrownBy(() -> alice.test(id, 1, UUID.randomUUID(), "x")).hasMessage("FORBIDDEN");
        assertThat(alice.audit(id, 0, 100).items()).extracting(AuditEvent::action)
                .containsExactly(AuditEvent.Action.DRAFT_CREATED, AuditEvent.Action.REVISION_SAVED,
                                 AuditEvent.Action.METADATA_UPDATED, AuditEvent.Action.DRAFT_ABANDONED);
    }

    @Test
    void concurrentWritersCommitExactlyOneRevisionAndOneAudit() throws Exception {
        // Given
        var alice = as("alice");
        var draft = draft(alice);
        var gate = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<String> save = () -> {
                gate.await();
                try {
                    alice.saveDraft(draft.draft().id(), 1, EMPTY);
                    return "saved";
                } catch (StudioProblem e) {
                    return e.code().name();
                }
            };
            // When
            var first = pool.submit(save);
            var second = pool.submit(save);
            gate.countDown();
            // Then
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("saved", "CONFLICT");
            assertThat(alice.revisions(draft.draft().id(), 0, 100).items()).hasSize(2);
            assertThat(alice.audit(draft.draft().id(), 0, 100).items())
                    .filteredOn(e -> e.action() == AuditEvent.Action.REVISION_SAVED).hasSize(1);
        } finally {
            gate.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void auditFailureRollsBackTheDefinitionAndRevisionHead() throws Exception {
        // Given
        var alice = as("alice");
        var original = draft(alice);
        try (var c = source.getConnection(); var sql = c.createStatement()) {
            sql.execute("ALTER TABLE g4js_audit ADD CONSTRAINT reject_save CHECK(action <> 'REVISION_SAVED')");
        }
        // When
        assertThatThrownBy(() -> alice.saveDraft(original.draft().id(), 1, EMPTY)).hasMessage("STORAGE_UNAVAILABLE");
        // Then
        assertThat(alice.draft(original.draft().id())).isEqualTo(original);
        assertThat(alice.revisions(original.draft().id(), 0, 100).items()).hasSize(1);
        assertThat(alice.audit(original.draft().id(), 0, 100).items()).hasSize(1);
    }

    @Test
    void inheritedRightsStopAtPrivateDraftsAndAuditPagesFilterInvisibleResources() {
        // Given
        var alice = as("alice");
        var bob = as("bob", "reviewers");
        var draft = draft(alice);
        var chain = (StudioChain) alice.resource(draft.draft().chainId()).resource();
        var domain = alice.resource(chain.domainId()).resource();
        alice.setAccess(domain.id(), 1, new AccessPolicy(false,
                List.of(grant(AccessGrant.SubjectType.GROUP, "reviewers", READ, CREATE_DRAFT, READ_AUDIT))));
        // When / Then
        assertThat(bob.resource(chain.id()).permissions()).contains(READ, CREATE_DRAFT);
        assertThatThrownBy(() -> bob.draft(draft.draft().id())).hasMessage("NOT_FOUND");
        assertThat(bob.audit(domain.id(), 0, 100).items()).noneMatch(e -> draft.draft().id().equals(e.draftId()));
        alice.setAccess(draft.draft().id(), 1, new AccessPolicy(true, List.of()));
        assertThat(bob.draft(draft.draft().id()).permissions()).contains(READ).doesNotContain(EDIT, TEST);
        var page = bob.audit(domain.id(), 0, 1);
        assertThat(page.items()).hasSize(1);
        assertThat(page.nextCursor()).isNotNull();
        assertThat(bob.audit(domain.id(), Long.parseLong(page.nextCursor()), 100).items())
                .noneMatch(e -> e.sequence() == page.items().get(0).sequence());
        alice.archive(domain.id(), 2);
        assertThat(bob.resource(chain.id()).permissions()).doesNotContain(CREATE_DRAFT, TEST, EDIT);
        assertThatThrownBy(() -> bob.createDraft(chain.id(), "Blocked", EMPTY)).hasMessage("FORBIDDEN");
    }

    @Test
    void filteredPaginationDoesNotUseHiddenRowsAsVisiblePages() {
        // Given
        var alice = as("alice");
        var bob = as("bob");
        for (int i = 0; i < 5; i++) {
            var domain = alice.createDomain("scope-" + i, "Scope " + i, "").resource();
            if (i % 2 == 0)
                alice.setAccess(domain.id(), 1, new AccessPolicy(false,
                        List.of(grant(AccessGrant.SubjectType.AUTHENTICATED, "*", DISCOVER))));
        }
        // When / Then
        String cursor = null;
        var ids = new java.util.HashSet<UUID>();
        int pages = 0;
        do {
            var page = bob.list(ResourceKind.DOMAIN, null, cursor, 1);
            assertThat(page.items()).hasSize(1);
            ids.add(page.items().get(0).id());
            cursor = page.nextCursor();
            pages++;
        } while (cursor != null);
        assertThat(ids).hasSize(3);
        assertThat(pages).isEqualTo(3);
    }

    @Test
    void testAdmissionIsAuditedWithoutPayloadAndDuplicateRequestsKeepTheirProvenance() {
        // Given
        var alice = as("alice");
        var draft = draft(alice);
        var id = UUID.randomUUID();
        // When
        var result = alice.test(draft.draft().id(), 1, id, "SENSITIVE-RUNTIME-INPUT");
        var changed = alice.saveDraft(draft.draft().id(), 1, EMPTY);
        // Then
        assertThat(alice.test(draft.draft().id(), 1, id, "SENSITIVE-RUNTIME-INPUT")).isEqualTo(result);
        assertThat(alice.testRequest(id).run().revisionId()).isEqualTo(draft.revision().revisionId())
                .isNotEqualTo(changed.revision().revisionId());
        assertThat(runtime.executions).hasValue(1);
        assertThatThrownBy(() -> alice.test(draft.draft().id(), 1, id, "other")).hasMessage("CONFLICT");
        var audit = alice.audit(draft.draft().id(), 0, 100).items();
        assertThat(audit.toString()).doesNotContain("SENSITIVE-RUNTIME-INPUT");
        assertThat(audit).filteredOn(e -> e.action() == AuditEvent.Action.TEST_REQUESTED).singleElement()
                .satisfies(e -> assertThat(e.requestId()).isEqualTo(id));
    }

    @Test
    void storageAdmissionAuthenticationAndGlobalDomainCreationFailClosed() {
        // Given
        var alice = as("alice");
        var draft = draft(alice);
        var anonymous = new StudioManagementService(store, runtime, () -> null, a -> true);
        var restricted = new StudioManagementService(store, runtime, () -> new StudioIdentity("bob", Set.of(), false),
                a -> false);
        // When / Then
        assertThatThrownBy(anonymous::identity).hasMessage("UNAUTHENTICATED");
        assertThat(restricted.canCreateDomain()).isFalse();
        assertThatThrownBy(() -> restricted.createDomain("denied", "Denied", "")).hasMessage("FORBIDDEN");
        assertThatThrownBy(() -> alice.saveDraft(draft.draft().id(), 1,
                                                 new ChainDefinition(1, "unsafe", "java.lang.String",
                                                         "java.lang.String", List.of())))
                .hasMessage("INVALID_DEFINITION");
        assertThat(alice.draft(draft.draft().id()).draft().version()).isEqualTo(1);
        assertThatThrownBy(() -> alice.list(ResourceKind.CHAIN, null, null, 101)).hasMessage("INVALID_REQUEST");
        assertThatThrownBy(() -> alice.createDomain("catalog", "Collision", "")).hasMessage("CONFLICT");
        assertThatThrownBy(() -> alice.createDomain("INVALID KEY", "Invalid", "")).hasMessage("INVALID_REQUEST");
        var imported = alice.importDraft(draft.draft().chainId(), "Import", "synthetic-xml");
        assertThat(imported.revision().origin()).isEqualTo(DraftRevision.Origin.XML_IMPORT);
        assertThat(alice.export(imported.draft().id(), EMPTY)).isEqualTo("synthetic-xml");
    }

    private static class StubRuntime implements StudioRuntime {
        final AtomicInteger executions = new AtomicInteger();

        public RuntimeContext context() {
            return new RuntimeContext("test", "build", "catalog", Set.of("VALIDATE", "CONTROLLED_TEST"));
        }

        public List<OperationDescriptor> catalog() {
            return List.of();
        }

        public void assertSafeToStore(ChainDefinition definition) {
            if (definition.id().equals("unsafe"))
                throw new StudioProblem(StudioProblem.Code.INVALID_DEFINITION);
        }

        public ValidationReport validate(ChainDefinition definition) {
            return new ValidationReport("catalog", List.of(Diagnostic.error("EMPTY", "/nodes", "Empty definition")));
        }

        public String exportDefinition(ChainDefinition definition, String mediaType) {
            return "synthetic-xml";
        }

        public ChainDefinition importDefinition(String source, String mediaType) {
            return EMPTY;
        }

        public ControlledRun execute(DraftRevision revision, String input, UUID requestId, String actor) {
            executions.incrementAndGet();
            return new ControlledRun(requestId, UUID.randomUUID(), revision.draftId(), revision.revisionId(),
                    revision.number(), actor,
                    "test", "build", "TEST", "catalog", "hash", "SUCCEEDED", Instant.now(), Instant.now(),
                    CapturedValue.none(), CapturedValue.none(), null, List.of());
        }
    }
}
