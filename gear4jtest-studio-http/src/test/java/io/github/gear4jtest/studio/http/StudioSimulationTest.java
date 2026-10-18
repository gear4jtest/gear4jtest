package io.github.gear4jtest.studio.http;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import io.github.gear4jtest.jdbc.persistence.Gear4jDatabaseDialect;
import io.github.gear4jtest.studio.demo.*;
import io.github.gear4jtest.studio.jdbc.*;
import io.github.gear4jtest.studio.json.StudioJson;
import io.github.gear4jtest.studio.management.*;
import io.github.gear4jtest.studio.model.*;
import io.github.gear4jtest.studio.runtime.LocalGear4jRuntime;
import io.github.gear4jtest.studio.runtime.OperationCatalog;
import io.github.gear4jtest.studio.spi.*;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static io.github.gear4jtest.studio.management.Permission.*;
import static org.assertj.core.api.Assertions.*;

class StudioSimulationTest {
    private ScenarioData data() {
        return new ScenarioData(new ScenarioInput(ScenarioInput.Kind.SYNTHETIC_TEXT, "  hello  ", null),
                new ScenarioExpectation("SUCCEEDED", "HELLO"),
                new SimulationProfile(1,
                        Map.of("trim",
                               new SimulationProfile.Mock("text.trim", SimulationProfile.Behavior.RETURN,
                                       SimulationProfile.Scalar.STRING, "hello"),
                               "uppercase", new SimulationProfile.Mock("text.uppercase",
                                       SimulationProfile.Behavior.RETURN, SimulationProfile.Scalar.STRING, "HELLO"))));
    }

    @Test
    void immutableReportsSurviveReopenRemainOutsideRunsAndRespectIndependentPermissionsAndRetriedRequests()
            throws Exception {
        // Given
        var db = new JdbcDataSource();
        db.setURL("jdbc:h2:mem:" + UUID.randomUUID());
        var actor = new AtomicReference<>(new StudioIdentity("alice", Set.of(), false));
        try (var lifetime = db.getConnection();
                var runtime = DemoWiring.runtime("simulation", null, null, DataCapturePolicy.allowed());
                var publisher = new DemoPublicationHost(db, DemoWiring.catalog())) {
            StudioJdbcSchema.migrate(db, Gear4jDatabaseDialect.H2);
            var store = new JdbcStudioStore(db);
            var service = new StudioManagementService(store, runtime, actor::get, a -> true, Clock.systemUTC(),
                    publisher.publisher(), TestReceiptPolicy.defaults(), new DemoScenarioPolicy());
            var domain = service.createDomain("domain", "Domain", "").resource();
            var chain = service.createChain(domain.id(), "chain", "Chain", "", "text_preparation").resource();
            var draft = service.createDraft(chain.id(), "Draft", DemoWiring.sample());
            var scenario = service.createScenario(chain.id(), "Scenario", data());
            var target = new ScenarioTarget(draft.draft().id(), 1L, null);
            var request = UUID.randomUUID();
            // When
            var report = service.simulate(scenario.scenario().id(), 1, target, request);
            assertThat(service.simulate(scenario.scenario().id(), 1, target, request)).isEqualTo(report);
            var reopened = new StudioManagementService(new JdbcStudioStore(db), runtime, actor::get, a -> true,
                    Clock.systemUTC(), publisher.publisher(), TestReceiptPolicy.defaults(), new DemoScenarioPolicy());
            // Then: durable provenance and one audit event; the runtime executor is
            // deliberately null.
            assertThat(reopened.simulation(request)).isEqualTo(report);
            assertThat(report.result().output().value()).isEqualTo("HELLO");
            assertThat(report.scenario().revisionId()).isEqualTo(scenario.revision().id());
            assertThat(report.definitionRevisionId()).isEqualTo(draft.revision().revisionId());
            assertThat(service.simulations(scenario.scenario().id(), null, 1).items()).containsExactly(report);
            assertThat(service.runs(chain.id(), new RunFilter(Instant.now().minusSeconds(60),
                    Instant.now().plusSeconds(60), null, null, null, null, null), null, 25).items()).isEmpty();
            assertThat(service.audit(draft.draft().id(), null, 100).items())
                    .filteredOn(e -> e.action() == AuditEvent.Action.SIMULATION_COMPLETED).hasSize(1);
            service.saveScenario(scenario.scenario().id(), 1, "Changed", data());
            assertThatThrownBy(() -> service.simulate(scenario.scenario().id(), 2, target, request))
                    .hasMessage("CONFLICT");
            var version = service.createVersion(draft.draft().id(), 1, "1.0.0").version();
            var versionReport = service.simulate(scenario.scenario().id(), 1,
                                                 new ScenarioTarget(null, null, version.id()), UUID.randomUUID());
            assertThat(versionReport.versionArtifactHash()).isEqualTo(version.artifactHash());
            assertThat(versionReport.target().versionId()).isEqualTo(version.id());
            // Independent SIMULATE right: no TEST right is required; captures require
            // observability permission.
            service.setAccess(chain.id(), 1,
                              new AccessPolicy(false, List.of(new AccessGrant(AccessGrant.SubjectType.USER, "bob",
                                      Set.of(READ, READ_SCENARIOS, READ_SIMULATIONS, SIMULATE)),
                                                              new AccessGrant(AccessGrant.SubjectType.USER, "reader",
                                                                      Set.of(READ, READ_SCENARIOS, READ_SIMULATIONS)),
                                                              new AccessGrant(AccessGrant.SubjectType.USER, "operator",
                                                                      Set.of(READ, READ_SCENARIOS, READ_RUNS,
                                                                             READ_OBSERVABILITY)),
                                                              new AccessGrant(AccessGrant.SubjectType.USER, "maker",
                                                                      Set.of(READ, READ_SCENARIOS, SIMULATE)))));
            service.setAccess(draft.draft().id(), 1, new AccessPolicy(true, List.of()));
            actor.set(new StudioIdentity("bob", Set.of(), false));
            assertThat(service.simulate(scenario.scenario().id(), 1, target, UUID.randomUUID()).result().output()
                    .state()).isEqualTo(CapturedValue.State.NOT_CAPTURED);
            assertThat(service.simulation(request).result().input().state())
                    .isEqualTo(CapturedValue.State.NOT_CAPTURED);
            assertThatThrownBy(() -> service.testScenario(scenario.scenario().id(), 1, target, UUID.randomUUID()))
                    .hasMessage("FORBIDDEN");
            actor.set(new StudioIdentity("reader", Set.of(), false));
            assertThat(service.simulation(request).id()).isEqualTo(request);
            assertThat(service.simulations(scenario.scenario().id(), null, 25).items()).isNotEmpty();
            assertThatThrownBy(() -> service.simulate(scenario.scenario().id(), 1, target, UUID.randomUUID()))
                    .hasMessage("FORBIDDEN");
            assertThatThrownBy(() -> service.runs(chain.id(), new RunFilter(Instant.now().minusSeconds(60),
                    Instant.now().plusSeconds(60), null, null, null, null, null), null, 25)).hasMessage("FORBIDDEN");
            actor.set(new StudioIdentity("operator", Set.of(), false));
            assertThatThrownBy(() -> service.simulation(request)).hasMessage("FORBIDDEN");
            assertThat(service.simulations(scenario.scenario().id(), null, 25).items()).isEmpty();
            actor.set(new StudioIdentity("maker", Set.of(), false));
            var own = UUID.randomUUID();
            assertThat(service.simulate(scenario.scenario().id(), 1, target, own).id()).isEqualTo(own);
            assertThat(service.simulate(scenario.scenario().id(), 1, target, own).id()).isEqualTo(own);
            assertThatThrownBy(() -> service.simulation(own)).hasMessage("FORBIDDEN");
            actor.set(new StudioIdentity("alice", Set.of(), false));
            service.setAccess(draft.draft().id(), 2, new AccessPolicy(false, List.of()));
            actor.set(new StudioIdentity("bob", Set.of(), false));
            assertThatThrownBy(() -> service.simulation(request)).hasMessage("NOT_FOUND");
            assertThat(service.simulations(scenario.scenario().id(), null, 25).items())
                    .allMatch(v -> v.target().versionId() != null);
            actor.set(new StudioIdentity("alice", Set.of(), false));
            var deleted = store
                    .write(tx -> tx.purgeSimulations(new StudioStore.InstantCutoff(Instant.now().plusSeconds(1), 1)));
            assertThat(deleted).isEqualTo(1);
        }
    }

    @ParameterizedTest
    @CsvSource({ "false,false", "true,false", "false,true", "true,true" })
    void historicalDriftIsAdvisoryVisibleBeforeSimulationAndDurableAfterReopen(boolean buildDrift, boolean catalogDrift)
            throws Exception {
        // Given: an immutable version created by an earlier host context.
        var db = new JdbcDataSource();
        db.setURL("jdbc:h2:mem:" + UUID.randomUUID());
        var catalog = DemoWiring.catalog();
        var previousCatalog = new OperationCatalog(catalog.descriptors().stream()
                .filter(d -> !catalogDrift || !d.id().equals("production.only"))
                .map(d -> catalog.find(d.id())).toList());
        var identity = new StudioIdentity("alice", Set.of(), false);
        try (var lifetime = db.getConnection();
                var oldRuntime = new LocalGear4jRuntime("stable-context", buildDrift ? "old-build" : "current-build",
                        previousCatalog, null, null, DataCapturePolicy.allowed(), Duration.ofSeconds(1));
                var currentRuntime = new LocalGear4jRuntime("stable-context", "current-build", catalog, null, null,
                        DataCapturePolicy.allowed(), Duration.ofSeconds(1));
                var publisher = new DemoPublicationHost(db, catalog)) {
            StudioJdbcSchema.migrate(db, Gear4jDatabaseDialect.H2);
            var store = new JdbcStudioStore(db);
            var old = new StudioManagementService(store, oldRuntime, () -> identity, a -> true, Clock.systemUTC(),
                    publisher.publisher(), TestReceiptPolicy.defaults(), new DemoScenarioPolicy());
            var domain = old.createDomain("d", "D", "").resource();
            var chain = old.createChain(domain.id(), "c", "C", "", "text_preparation").resource();
            var draft = old.createDraft(chain.id(), "Draft", DemoWiring.sample());
            var scenario = old.createScenario(chain.id(), "Scenario", data());
            var version = old.createVersion(draft.draft().id(), 1, "1.0.0").version();
            var target = new ScenarioTarget(null, null, version.id());
            var current = new StudioManagementService(store, currentRuntime, () -> identity, a -> true,
                    Clock.systemUTC(),
                    publisher.publisher(), TestReceiptPolicy.defaults(), new DemoScenarioPolicy());
            // When
            var plan = current.planSimulation(scenario.scenario().id(), 1, target);
            var report = current.simulate(scenario.scenario().id(), 1, target, UUID.randomUUID());
            var expectedPaths = new ArrayList<String>();
            if (buildDrift)
                expectedPaths.add("/context/applicationBuild");
            if (catalogDrift)
                expectedPaths.add("/context/catalogFingerprint");
            // Then: no false warning on unchanged provenance or a draft; warning does
            // not turn an otherwise valid plan into an error.
            assertThat(plan.valid()).isTrue();
            assertThat(plan.diagnostics()).extracting(Diagnostic::path).containsExactlyElementsOf(expectedPaths);
            assertThat(plan.diagnostics()).allSatisfy(d -> {
                assertThat(d.severity()).isEqualTo(Diagnostic.Severity.WARNING);
                assertThat(d.code()).isEqualTo("HISTORICAL_CONTEXT_DIFFERS");
            });
            assertThat(report.diagnostics()).isEqualTo(plan.diagnostics());
            assertThat(report.applicationBuild()).isEqualTo("current-build");
            assertThat(report.result().catalogFingerprint()).isEqualTo(catalog.fingerprint());
            assertThat(report.withoutCaptures().diagnostics()).isEqualTo(report.diagnostics());
            assertThat(current.planSimulation(scenario.scenario().id(), 1,
                                              new ScenarioTarget(draft.draft().id(), 1L, null))
                    .diagnostics()).isEmpty();
            var reopened = new StudioManagementService(new JdbcStudioStore(db), oldRuntime, () -> identity, a -> true);
            assertThat(reopened.simulation(report.id())).isEqualTo(report);
            assertThat(reopened.simulations(scenario.scenario().id(), null, 25).items()).containsExactly(report);
        }
    }

    @Test
    void httpRouteContractPersistsMockDataExactlyAndRejectsUnsafeOrUnstructuredSimulationInputs() throws Exception {
        // Given
        var db = new JdbcDataSource();
        db.setURL("jdbc:h2:mem:" + UUID.randomUUID());
        try (var lifetime = db.getConnection();
                var runtime = DemoWiring.runtime("simulation", null, null, DataCapturePolicy.none())) {
            StudioJdbcSchema.migrate(db, Gear4jDatabaseDialect.H2);
            var store = new JdbcStudioStore(db);
            var service = new StudioManagementService(store, runtime,
                    () -> new StudioIdentity("alice", Set.of(), false), a -> true, Clock.systemUTC(), null,
                    TestReceiptPolicy.defaults(), new DemoScenarioPolicy());
            var domain = service.createDomain("d", "D", "").resource();
            var chain = service.createChain(domain.id(), "c", "C", "", "text_preparation").resource();
            var draft = service.createDraft(chain.id(), "Draft", DemoWiring.sample());
            var routes = new ManagementRoutes(service, DemoWiring.sample());
            var json = StudioJson.mapper();
            var response = routes.handle("POST", "/api/v1/resources/" + draft.draft().id() + "/scenarios", Map.of(),
                                         json.valueToTree(Map.of("name", "Simulation", "data", data())));
            var scenario = (ScenarioState) response.value();
            var target = new ScenarioTarget(draft.draft().id(), 1L, null);
            var request = json.valueToTree(Map.of("revision", 1, "target", target));
            assertThat(((Map<?, ?>) routes
                    .handle("POST", "/api/v1/scenarios/" + scenario.scenario().id() + "/simulation-plan", Map.of(),
                            request)
                    .value()).get("valid")).isEqualTo(true);
            var id = UUID.randomUUID();
            var execute = json.valueToTree(Map.of("revision", 1, "target", target, "requestId", id));
            var report = (StudioSimulation) routes
                    .handle("POST", "/api/v1/scenarios/" + scenario.scenario().id() + "/simulations", Map.of(), execute)
                    .value();
            assertThat(routes.handle("GET", "/api/v1/simulations/" + id, Map.of(), null).value()).isEqualTo(report);
            assertThat(routes
                    .handle("GET", "/api/v1/scenarios/" + scenario.scenario().id() + "/simulations", Map.of(), null)
                    .value()).isNotNull();
            var unsafe = new ScenarioData(data().input(), null,
                    new SimulationProfile(1,
                            Map.of("trim", new SimulationProfile.Mock("text.trim", SimulationProfile.Behavior.RETURN,
                                    SimulationProfile.Scalar.STRING, "credential-like-unapproved-value"))));
            assertThatThrownBy(() -> service.saveScenario(scenario.scenario().id(), 1, "Unsafe", unsafe))
                    .hasMessage("SCENARIO_DATA_NOT_ADMITTED");
            assertThat(service.scenario(scenario.scenario().id()).revision().number()).isEqualTo(1);
            service.saveSource(draft.draft().id(), 1, new DraftRevision.Source("application/xml", "<assemblyLine>"));
            assertThatThrownBy(() -> service.planSimulation(scenario.scenario().id(), 1,
                                                            new ScenarioTarget(draft.draft().id(), 2L, null)))
                    .hasMessage("SOURCE_REQUIRES_ANALYSIS");
            var noPolicy = new StudioManagementService(store, runtime,
                    () -> new StudioIdentity("alice", Set.of(), false), a -> true);
            assertThat(noPolicy.context().capabilities()).doesNotContain("SIMULATION");
            assertThatThrownBy(() -> noPolicy.simulate(scenario.scenario().id(), 1, target, UUID.randomUUID()))
                    .hasMessage("FORBIDDEN");
        }
    }
}
