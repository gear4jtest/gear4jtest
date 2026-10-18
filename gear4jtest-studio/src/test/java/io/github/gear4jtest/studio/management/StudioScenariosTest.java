package io.github.gear4jtest.studio.management;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import io.github.gear4jtest.jdbc.persistence.Gear4jDatabaseDialect;
import io.github.gear4jtest.studio.jdbc.JdbcStudioStore;
import io.github.gear4jtest.studio.jdbc.StudioJdbcSchema;
import io.github.gear4jtest.studio.model.*;
import io.github.gear4jtest.studio.spi.*;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static io.github.gear4jtest.studio.management.Permission.*;
import static org.assertj.core.api.Assertions.*;

class StudioScenariosTest {
    private static final ChainDefinition DEFINITION = new ChainDefinition(1, "sample", "java.lang.String",
            "java.lang.String", List.of());
    private final JdbcDataSource source = new JdbcDataSource();
    private final JdbcStudioStore store;
    private final Runtime runtime = new Runtime();
    private final Policy policy = new Policy();
    private final StudioManagementService alice;
    private final DraftState draft;

    StudioScenariosTest() {
        source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        StudioJdbcSchema.migrate(source, Gear4jDatabaseDialect.H2);
        store = new JdbcStudioStore(source);
        alice = as("alice", policy);
        var domain = alice.createDomain("d", "D", "").resource();
        var chain = alice.createChain(domain.id(), "c", "C", "", "sample").resource();
        draft = alice.createDraft(chain.id(), "Private", DEFINITION);
    }

    @AfterEach
    void close() throws Exception {
        try (var c = source.getConnection(); var s = c.createStatement()) {
            s.execute("SHUTDOWN");
        }
    }

    private StudioManagementService as(String user, ScenarioDataPolicy policy) {
        return new StudioManagementService(store, runtime, () -> new StudioIdentity(user, Set.of(), false), a -> true,
                Clock.systemUTC(), null, TestReceiptPolicy.defaults(), policy);
    }

    private ScenarioData data(String input) {
        return new ScenarioData(new ScenarioInput(ScenarioInput.Kind.SYNTHETIC_TEXT, input, null),
                new ScenarioExpectation("SUCCEEDED", "HELLO"));
    }

    private ScenarioTarget target() {
        return new ScenarioTarget(draft.draft().id(), 1L, null);
    }

    private ScenarioState create(UUID scope) {
        return alice.createScenario(scope, "Greeting", data("hello"));
    }

    private void share(UUID scope, long expected, Permission... permissions) {
        alice.setAccess(scope, expected, new AccessPolicy(false,
                List.of(new AccessGrant(AccessGrant.SubjectType.USER, "bob", Set.of(permissions)))));
    }

    @Test
    void syntheticTextIsReplayedExactlyWithoutCallingTheResolver() {
        // Given: the resolver would substitute a different value if called.
        var exact = "  hello\n";
        var admitting = new ScenarioDataPolicy() {
            public void assertSafeToStore(StudioIdentity actor, UUID scope, ScenarioData data) {
            }

            public ResolvedScenarioInput resolveFixture(StudioIdentity actor, UUID scope, ScenarioInput input) {
                throw new AssertionError("A synthetic value must not be resolved");
            }
        };
        var service = as("alice", admitting);
        var scenario = service.createScenario(draft.draft().id(), "Exact", data(exact));
        // When
        var result = service.testScenario(scenario.scenario().id(), 1, target(), UUID.randomUUID());
        // Then
        assertThat(runtime.lastInput).isEqualTo(exact);
        assertThat(result.run().fixture()).isNull();
        assertThat(service.scenario(scenario.scenario().id()).revision().data().input().value()).isEqualTo(exact);
    }

    @Test
    void resolvedFingerprintsAreDurableDistinguishDriftAndRequireScenarioReadPermission() {
        // Given
        var reference = new ScenarioInput(ScenarioInput.Kind.FIXTURE_REFERENCE, "greeting", "1");
        var scenario = alice.createScenario(draft.draft().id(), "Reference", new ScenarioData(reference, null));
        var request = UUID.randomUUID();
        // When
        var first = alice.testScenario(scenario.scenario().id(), 1, target(), request).run();
        var same = alice.testScenario(scenario.scenario().id(), 1, target(), UUID.randomUUID()).run();
        policy.value = "world";
        var changed = alice.testScenario(scenario.scenario().id(), 1, target(), UUID.randomUUID()).run();
        // Then: identity includes the resolved fingerprint as well as the actual input.
        assertThat(first.fixture()).isEqualTo(same.fixture());
        assertThat(changed.fixture().fingerprint()).isNotEqualTo(first.fixture().fingerprint());
        assertThat(first.fixture().reference()).isEqualTo("greeting");
        assertThat(first.fixture().version()).isEqualTo("1");
        assertThatThrownBy(() -> alice.testScenario(scenario.scenario().id(), 1, target(), request))
                .hasMessage("CONFLICT");
        var saved = store.read(tx -> tx.runs(draft.draft().chainId(), draft.draft().id(),
                                             new RunFilter(Instant.now().minusSeconds(60),
                                                     Instant.now().plusSeconds(60), null, null, null, null, null),
                                             null, 100))
                .stream().filter(r -> r.provenance().requestId().equals(request)).findFirst().orElseThrow();
        assertThat(as("alice", policy).run(saved.id()).provenance().fixture()).isEqualTo(first.fixture());
        assertThat(saved.observation().input().state()).isEqualTo(CapturedValue.State.NOT_CAPTURED);
        share(draft.draft().id(), 1, READ, READ_RUNS, READ_OBSERVABILITY);
        assertThat(as("bob", policy).run(saved.id()).provenance().fixture()).isNull();
        share(draft.draft().id(), 2, READ, READ_RUNS, READ_SCENARIOS);
        assertThat(as("bob", policy).run(saved.id()).provenance().fixture()).isEqualTo(first.fixture());
        policy.keyId = "rotated";
        var rotated = alice.testScenario(scenario.scenario().id(), 1, target(), UUID.randomUUID()).run();
        assertThat(rotated.fixture().fingerprint().keyId()).isNotEqualTo(changed.fixture().fingerprint().keyId());
    }

    @Test
    void fingerprintRejectsInvalidTextAndNeverPrintsResolvedInput() {
        // Given / When / Then
        var key = new javax.crypto.spec.SecretKeySpec(new byte[32], "HmacSHA256");
        var input = ResolvedScenarioInput.hmacSha256("sensitive", "key-1", key);
        assertThat(input.toString()).doesNotContain("sensitive");
        assertThat(input.fingerprint().value()).hasSize(64);
        assertThatThrownBy(() -> ResolvedScenarioInput.hmacSha256("\uD800", "key-1", key))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ResolvedScenarioInput.hmacSha256("x", "invalid/key", key))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void immutableRevisionsPaginationAndAuditSurviveReopeningWithoutEmbeddingFixturesInAudit() {
        // Given
        var first = create(draft.draft().id());
        var second = create(draft.draft().id());
        // When
        var saved = alice.saveScenario(first.scenario().id(), 1, "Updated", data("world"));
        var reopened = as("alice", policy);
        // Then
        assertThat(reopened.scenario(first.scenario().id())).isEqualTo(saved);
        var page = reopened.scenarioRevisions(first.scenario().id(), null, 1);
        assertThat(page.items()).singleElement().satisfies(r -> assertThat(r.data()).isEqualTo(data("hello")));
        assertThat(reopened.scenarioRevisions(first.scenario().id(), page.nextCursor(), 1).items())
                .containsExactly(saved.revision());
        var scenarios = reopened.scenarios(draft.draft().id(), null, 1);
        assertThat(scenarios.items()).hasSize(1);
        assertThat(scenarios.nextCursor()).isNotNull();
        assertThat(reopened.scenarios(draft.draft().id(), scenarios.nextCursor(), 1).items()).hasSize(1);
        assertThatThrownBy(() -> reopened.scenarios(draft.draft().chainId(), scenarios.nextCursor(), 1))
                .hasMessage("INVALID_REQUEST");
        assertThatThrownBy(() -> reopened.scenarios(draft.draft().id(), null, 101)).hasMessage("INVALID_REQUEST");
        assertThatThrownBy(() -> alice.saveScenario(first.scenario().id(), 1, "Conflict", data("hello")))
                .hasMessage("CONFLICT");
        assertThat(alice.audit(draft.draft().id(), null, 100).items()).filteredOn(e -> e.scenario() != null)
                .extracting(AuditEvent::scenario).containsExactly(first.revision().reference(),
                                                                  second.revision().reference(),
                                                                  saved.revision().reference());
        assertThat(alice.archiveScenario(first.scenario().id(), 2).actions()).isEmpty();
        assertThatThrownBy(() -> alice.testScenario(first.scenario().id(), 1, target(), UUID.randomUUID()))
                .hasMessage("FORBIDDEN");
        assertThatThrownBy(() -> alice.saveScenario(first.scenario().id(), 3, "No", data("hello")))
                .hasMessage("FORBIDDEN");
        assertThat(alice.scenarioRevisions(first.scenario().id(), null, 100).items()).hasSize(2);
    }

    @Test
    void scenarioPermissionsAndPrivateDraftScopeAreEnforcedSeparatelyFromTargetTestPermission() {
        // Given
        var chain = create(draft.draft().chainId());
        var privateScenario = create(draft.draft().id());
        var bob = as("bob", policy);
        share(draft.draft().chainId(), 1, READ, TEST);
        // When / Then
        assertThatThrownBy(() -> bob.scenario(chain.scenario().id())).hasMessage("FORBIDDEN");
        assertThatThrownBy(() -> bob.scenario(privateScenario.scenario().id())).hasMessage("NOT_FOUND");
        share(draft.draft().chainId(), 2, READ, READ_SCENARIOS);
        share(draft.draft().id(), 1, READ, READ_SCENARIOS);
        assertThat(bob.scenario(chain.scenario().id()).actions()).doesNotContain("EDIT");
        assertThatThrownBy(() -> bob.testScenario(chain.scenario().id(), 1, target(), UUID.randomUUID()))
                .hasMessage("FORBIDDEN");
        assertThatThrownBy(() -> bob.saveScenario(chain.scenario().id(), 1, "No", data("hello")))
                .hasMessage("FORBIDDEN");
        share(draft.draft().id(), 2, READ, READ_SCENARIOS, TEST);
        var request = UUID.randomUUID();
        assertThat(bob.testScenario(chain.scenario().id(), 1, target(), request).run().scenario())
                .isEqualTo(chain.revision().reference());
        share(draft.draft().chainId(), 3, READ);
        assertThatThrownBy(() -> bob.testRequest(request)).hasMessage("FORBIDDEN");
        var other = alice.createDraft(draft.draft().chainId(), "Other", DEFINITION);
        assertThatThrownBy(() -> alice.testScenario(privateScenario.scenario().id(), 1,
                                                    new ScenarioTarget(other.draft().id(), 1L, null),
                                                    UUID.randomUUID()))
                .hasMessage("SCENARIO_TARGET_MISMATCH");
    }

    @Test
    void receiptIdentityPinsBothRevisionsAndPersistsChecksAndProvenanceExactlyOnce() {
        // Given
        var scenario = create(draft.draft().id());
        var request = UUID.randomUUID();
        // When
        var result = alice.testScenario(scenario.scenario().id(), 1, target(), request);
        var saved = alice.saveScenario(scenario.scenario().id(), 1, "Next", data("world"));
        // Then: a head change does not change the previously selected revision.
        assertThat(alice.testScenario(scenario.scenario().id(), 1, target(), request)).isSameAs(result);
        assertThat(runtime.calls).hasValue(1);
        assertThatThrownBy(() -> alice.testScenario(scenario.scenario().id(), 2, target(), request))
                .hasMessage("CONFLICT");
        var failedCheck = alice.testScenario(scenario.scenario().id(), 2, target(), UUID.randomUUID());
        assertThat(failedCheck.run().outcome()).isEqualTo("SUCCEEDED");
        assertThat(failedCheck.run().scenarioCheck().status()).isEqualTo(ScenarioCheck.Status.FAILED);
        assertThat(failedCheck.run().scenarioCheck().differences()).containsExactly("OUTPUT_MISMATCH");
        var filter = new RunFilter(Instant.now().minusSeconds(60), Instant.now().plusSeconds(60), null, null, null,
                null, null);
        var runs = alice.runs(draft.draft().id(), filter, null, 100).items();
        assertThat(runs).hasSize(2);
        var run = runs.stream().filter(r -> r.provenance().scenario().revisionId().equals(saved.revision().id()))
                .findFirst().orElseThrow();
        assertThat(alice.run(run.id()).observation().scenarioCheck()).isEqualTo(failedCheck.run().scenarioCheck());
        assertThat(alice.testRequest(request)).isSameAs(result);
    }

    @Test
    void admissionFailsClosedOnSecretsDisabledPolicyRevokedFixturesOrAclChangedDuringResolution() {
        // Given
        var disabled = as("alice", null);
        assertThat(disabled.context().capabilities()).doesNotContain("SCENARIOS");
        assertThatThrownBy(() -> disabled.createScenario(draft.draft().id(), "No", data("hello")))
                .hasMessage("FORBIDDEN");
        assertThatThrownBy(() -> alice.createScenario(draft.draft().id(), "No", data("secret")))
                .hasMessage("SCENARIO_DATA_NOT_ADMITTED");
        assertThat(alice.scenarios(draft.draft().id(), null, 100).items()).isEmpty();
        var data = new ScenarioData(new ScenarioInput(ScenarioInput.Kind.FIXTURE_REFERENCE, "greeting", "1"), null);
        var scenario = alice.createScenario(draft.draft().id(), "Reference", data);
        policy.revoked = true;
        // When / Then
        assertThatThrownBy(() -> alice.testScenario(scenario.scenario().id(), 1, target(), UUID.randomUUID()))
                .hasMessage("SCENARIO_DATA_NOT_ADMITTED");
        assertThat(runtime.calls).hasValue(0);
        policy.revoked = false;
        share(draft.draft().id(), 1, READ, READ_SCENARIOS, TEST);
        policy.onResolve = () -> share(draft.draft().id(), 2, READ, READ_SCENARIOS);
        assertThat(as("bob", policy).testScenario(scenario.scenario().id(), 1, target(), UUID.randomUUID()).state())
                .isEqualTo(TestSubmission.State.PREPARATION_FAILED);
        assertThat(runtime.calls).hasValue(0);
        assertThat(alice.audit(draft.draft().id(), null, 100).items())
                .noneMatch(e -> e.action() == AuditEvent.Action.TEST_REQUESTED);
    }

    @Test
    void invalidSourceAndUnknownTargetsAreNeverExecuted() {
        // Given
        var scenario = create(draft.draft().id());
        alice.saveSource(draft.draft().id(), 1, new DraftRevision.Source("application/xml", "<assemblyLine>"));
        // When / Then
        assertThatThrownBy(() -> alice.testScenario(scenario.scenario().id(), 1,
                                                    new ScenarioTarget(draft.draft().id(), 2L, null),
                                                    UUID.randomUUID()))
                .hasMessage("SOURCE_REQUIRES_ANALYSIS");
        assertThatThrownBy(() -> alice.testScenario(scenario.scenario().id(), 999, target(), UUID.randomUUID()))
                .hasMessage("NOT_FOUND");
        assertThatThrownBy(() -> alice.testScenario(scenario.scenario().id(), 1,
                                                    new ScenarioTarget(null, null, UUID.randomUUID()),
                                                    UUID.randomUUID()))
                .hasMessage("NOT_FOUND");
        assertThat(runtime.calls).hasValue(0);
        assertThat(ScenarioCheck.evaluate(null, "FAILED", new Object()).status())
                .isEqualTo(ScenarioCheck.Status.NOT_REQUESTED);
        assertThat(ScenarioCheck.evaluate(new ScenarioExpectation("SUCCEEDED", "hello"), "FAILED", null).differences())
                .containsExactly("OUTCOME_MISMATCH", "OUTPUT_MISMATCH");
    }

    @Test
    void preparationFailureKeepsScenarioProvenanceWithoutInventingAnAssertionResult() {
        // Given
        var s = create(draft.draft().id());
        runtime.fail = true;
        // When / Then
        assertThat(alice.testScenario(s.scenario().id(), 1, target(), UUID.randomUUID()).state())
                .isEqualTo(TestSubmission.State.PREPARATION_FAILED);
        var filter = new RunFilter(Instant.now().minusSeconds(60), Instant.now().plusSeconds(60), null, null, null,
                null, null);
        var run = alice.run(alice.runs(draft.draft().id(), filter, null, 10).items().get(0).id());
        assertThat(run.provenance().scenario()).isEqualTo(s.revision().reference());
        assertThat(run.status()).isEqualTo("PREPARATION_FAILED");
        assertThat(run.observation().scenarioCheck().status()).isEqualTo(ScenarioCheck.Status.NOT_EVALUATED);
    }

    @Test
    void stationMetricsSeparateModesArtifactsCatalogsBuildsAndUnattestedRuns() {
        // Given: the same nodeId deliberately refers to several implementations/content
        // identities.
        var recorder = new StudioRunRecorder(store, "test", Clock.systemUTC());
        String[][] dimensions = { { "TEST", "a", "catalog", "build" }, { "RUN", "a", "catalog", "build" },
                { "RUN", "b", "catalog", "build" }, { "RUN", "a", "changed-catalog", "build" },
                { "RUN", "a", "catalog", "changed-build" }, { "RUN", null, null, "build" },
                { "RUN", null, null, "build" } };
        for (var d : dimensions) {
            var hash = d[1] == null ? null : d[1].repeat(64);
            UUID id = recorder.beginHostRun(draft.draft().chainId(), new StudioRun.Provenance("test", "sample", d[3],
                    d[2], hash, "host", d[0], null, null, null, 0, null, null));
            var now = Instant.now();
            recorder.record(id, new RunObservation(UUID.randomUUID(), null, null, "SUCCEEDED", now, now,
                    CapturedValue.none(), CapturedValue.none(), null,
                    List.of(new ControlledRun.Step(UUID.randomUUID(), "same-node", "SUCCEEDED", now, now)), false,
                    hash));
        }
        // When / Then
        var metrics = alice.metrics(draft.draft().chainId(), new RunFilter(Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(60), null, null, null, null, null));
        assertThat(metrics.operations()).hasSize(7).allSatisfy(o -> assertThat(o.executions()).isEqualTo(1));
        assertThat(metrics.operations()).filteredOn(o -> o.dimension().unattestedRunId() != null).hasSize(2);
        assertThat(metrics.operations()).extracting(o -> o.dimension().mode()).contains("TEST", "RUN");
    }

    private static final class Policy implements ScenarioDataPolicy {
        String value = "hello";
        String keyId = "test-fixtures-v1";
        boolean revoked;
        Runnable onResolve = () -> {
        };

        public void assertSafeToStore(StudioIdentity actor, UUID scope, ScenarioData data) {
            if (data.input().kind() == ScenarioInput.Kind.SYNTHETIC_TEXT
                    && !Set.of("hello", "world").contains(data.input().value()))
                throw new IllegalArgumentException("secret");
        }

        public ResolvedScenarioInput resolveFixture(StudioIdentity actor, UUID scope, ScenarioInput input) {
            if (revoked)
                throw new IllegalStateException("secret provider error");
            onResolve.run();
            assertThat(input.kind()).isEqualTo(ScenarioInput.Kind.FIXTURE_REFERENCE);
            return ResolvedScenarioInput.hmacSha256(value, keyId,
                                                    new javax.crypto.spec.SecretKeySpec(new byte[32], "HmacSHA256"));
        }
    }

    private static final class Runtime implements StudioRuntime {
        final AtomicInteger calls = new AtomicInteger();
        boolean fail;
        String lastInput;

        public RuntimeContext context() {
            return new RuntimeContext("test", "build", "catalog", Set.of("SCENARIO_TEST"));
        }

        public List<OperationDescriptor> catalog() {
            return List.of();
        }

        public void assertSafeToStore(ChainDefinition definition) {
        }

        public void assertSafeSourceToStore(DraftRevision.Source source) {
        }

        public ValidationReport validate(ChainDefinition definition) {
            return new ValidationReport("catalog", List.of());
        }

        public String exportDefinition(ChainDefinition definition, String mediaType) {
            return "artifact";
        }

        public ChainDefinition importDefinition(String source, String mediaType) {
            return DEFINITION;
        }

        public ControlledRun execute(DraftRevision r, String input, UUID requestId, String actor) {
            lastInput = input;
            calls.incrementAndGet();
            if (fail)
                throw new io.github.gear4jtest.studio.service.StudioProblem(
                        io.github.gear4jtest.studio.service.StudioProblem.Code.INVALID_DEFINITION);
            return new ControlledRun(requestId, UUID.randomUUID(), r.draftId(), r.revisionId(), r.number(), actor,
                    "test", "build", "TEST", "catalog", "a".repeat(64), "SUCCEEDED", Instant.now(), Instant.now(),
                    CapturedValue.none(), CapturedValue.none(), null, List.of());
        }

        public ControlledRun executeScenario(DraftRevision r,
                                             String input,
                                             UUID requestId,
                                             String actor,
                                             ScenarioData data,
                                             Consumer<RunObservation> observer) {
            return execute(r, input, requestId, actor).withScenario(null, ScenarioCheck
                    .evaluate(data.expected(), "SUCCEEDED", input.toUpperCase(java.util.Locale.ROOT)));
        }
    }
}
