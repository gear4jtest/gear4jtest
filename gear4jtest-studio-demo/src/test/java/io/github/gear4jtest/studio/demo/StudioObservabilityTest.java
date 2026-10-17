package io.github.gear4jtest.studio.demo;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.github.gear4jtest.core.api.RunRequest;
import io.github.gear4jtest.core.api.util.AssemblyLines;
import io.github.gear4jtest.core.api.util.Stations;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import io.github.gear4jtest.jdbc.persistence.Gear4jDatabaseDialect;
import io.github.gear4jtest.studio.jdbc.JdbcStudioStore;
import io.github.gear4jtest.studio.jdbc.StudioJdbcSchema;
import io.github.gear4jtest.studio.management.*;
import io.github.gear4jtest.studio.model.*;
import io.github.gear4jtest.studio.runtime.*;
import io.github.gear4jtest.studio.spi.DataCapturePolicy;
import io.github.gear4jtest.studio.spi.StudioPublisher;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static io.github.gear4jtest.studio.management.Permission.*;
import static org.assertj.core.api.Assertions.*;

class StudioObservabilityTest {
    @TempDir
    Path directory;

    private JdbcDataSource source() {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:file:" + directory.resolve("observations"));
        StudioJdbcSchema.migrate(source, Gear4jDatabaseDialect.H2);
        return source;
    }

    private StudioManagementService service(JdbcDataSource source,
                                            LocalGear4jRuntime runtime,
                                            StudioPublisher publisher,
                                            String actor) {
        return new StudioManagementService(new JdbcStudioStore(source), runtime,
                () -> new StudioIdentity(actor, Set.of(), false), a -> true, publisher);
    }

    private DraftState draft(StudioManagementService service) {
        var domain = service.createDomain("catalog", "Catalogue", "").resource();
        var chain = service.createChain(domain.id(), "text", "Text", "", "text_preparation").resource();
        return service.createDraft(chain.id(), "Private work", DemoWiring.sample());
    }

    private RunFilter filter() {
        return new RunFilter(Instant.now().minusSeconds(3600), Instant.now().plusSeconds(3600), null, null, null, null,
                null);
    }

    @Test
    void exactMaskedSnapshotsSurviveReopenAndReceiptReplayDoesNotCreateAnotherRun() throws Exception {
        // Given
        var source = source();
        UUID scope;
        UUID id;
        UUID engine;
        var resources = DemoWiring.plainResources();
        try (var runtime = DemoWiring.runtime("observations", DemoWiring.plainExecutor(resources), resources,
                                              DataCapturePolicy.masked(value -> "[redacted]"))) {
            var service = service(source, runtime, null, "alice");
            var draft = draft(service);
            scope = draft.draft().id();
            var request = UUID.randomUUID();
            // When
            var submitted = service.test(scope, 1, request, "synthetic-private-value");
            assertThat(submitted.state()).isEqualTo(TestSubmission.State.COMPLETED);
            engine = submitted.run().runId();
            assertThat(service.test(scope, 1, request, "synthetic-private-value")).isSameAs(submitted);
            var page = service.runs(scope, filter(), null, 10);
            // Then
            assertThat(page.items()).hasSize(1);
            id = page.items().get(0).id();
            assertThat(page.items().get(0).observation()).isNull();
            var run = service.run(id);
            assertThat(run.provenance().revisionId()).isEqualTo(draft.revision().revisionId());
            assertThat(run.provenance().artifactHash()).isEqualTo(submitted.run().artifactHash());
            assertThat(run.observation().runId()).isEqualTo(engine);
            assertThat(run.observation().input())
                    .isEqualTo(new CapturedValue(CapturedValue.State.REDACTED, "[redacted]"));
            assertThat(run.observation().steps()).extracting(ControlledRun.Step::nodeId).contains("trim", "uppercase");
            assertThat(service.metrics(scope, filter()).statuses()).containsEntry("SUCCEEDED", 1L);
            try (var c = source.getConnection();
                    var s = c.createStatement();
                    var rows = s.executeQuery("SELECT snapshot_json FROM g4js_run")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).doesNotContain("synthetic-private-value", "SYNTHETIC-PRIVATE-VALUE");
            }
        }
        try (var runtime = DemoWiring.runtime("observations", DemoWiring.plainExecutor(resources), resources,
                                              DataCapturePolicy.none())) {
            var reopened = service(source, runtime, null, "alice");
            assertThat(reopened.run(id).status()).isEqualTo("SUCCEEDED");
            var window = filter();
            var exact = new RunFilter(window.from(), window.until(), "SUCCEEDED", "TEST", null, engine, 0L);
            assertThat(reopened.runs(scope, exact, null, 10).items()).extracting(StudioRun::id).containsExactly(id);
        }
    }

    @Test
    void chainHistoryMetricsAndDetailsRespectPrivateDraftsAndCurrentSeparatePermissions() {
        // Given
        var source = source();
        var resources = DemoWiring.plainResources();
        try (var runtime = DemoWiring.runtime("observations", DemoWiring.plainExecutor(resources), resources,
                                              DataCapturePolicy.allowed());
                var host = new DemoPublicationHost(source, DemoWiring.catalog())) {
            var alice = service(source, runtime, host.publisher(), "alice");
            var bob = service(source, runtime, host.publisher(), "bob");
            var draft = draft(alice);
            var chain = draft.draft().chainId();
            alice.test(draft.draft().id(), 1, UUID.randomUUID(), "private");
            UUID privateRun = alice.runs(draft.draft().id(), filter(), null, 10).items().get(0).id();
            var version = alice.createVersion(draft.draft().id(), 1, "1").version();
            alice.testVersion(version.id(), UUID.randomUUID(), "chain-visible");
            alice.setAccess(chain, 1, new AccessPolicy(true, List.of(new AccessGrant(AccessGrant.SubjectType.USER,
                    "bob", Set.of(READ, READ_RUNS)))));
            // When / Then
            var page = bob.runs(chain, filter(), null, 10);
            assertThat(page.items()).hasSize(1);
            UUID id = page.items().get(0).id();
            assertThat(bob.run(id).observation()).isNull();
            assertThatThrownBy(() -> bob.run(privateRun)).hasMessage("NOT_FOUND");
            assertThatThrownBy(() -> bob.metrics(chain, filter())).hasMessage("FORBIDDEN");
            alice.setAccess(chain, 2, new AccessPolicy(true, List.of(new AccessGrant(AccessGrant.SubjectType.USER,
                    "bob", Set.of(READ, READ_RUNS, READ_OBSERVABILITY)))));
            assertThat(bob.metrics(chain, filter()).total()).isEqualTo(1);
            assertThat(bob.run(id).observation().output().value()).isEqualTo("CHAIN-VISIBLE");
            alice.setAccess(chain, 3, new AccessPolicy(true, List.of()));
            assertThatThrownBy(() -> bob.run(id)).hasMessage("NOT_FOUND");
            assertThatThrownBy(() -> bob.runs(chain, filter(), null, 10)).hasMessage("NOT_FOUND");
        }
    }

    @Test
    void activeSnapshotsAndProgrammaticHostRunsUseTheSameDurableReadModelAndRetention() throws Exception {
        // Given
        var source = source();
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var hold = new HoldOperation(started, release);
        ResourceFactory resources = new ResourceFactory() {
            @Override
            public <T> T getResource(Class<T> type) {
                return type.cast(hold);
            }
        };
        var catalog = new OperationCatalog(
                List.of(RegisteredOperation.inferred("text.hold", "Hold", "Fixture", "Synthetic",
                                                     HoldOperation.class, true, true, Map.of(), Map.of())));
        var pool = Executors.newSingleThreadExecutor();
        var executor = DemoWiring.plainExecutor(resources);
        try (var dispatcher = new RunObservationDispatcher();
                var runtime = new LocalGear4jRuntime("observations", "build", catalog, executor, resources,
                        DataCapturePolicy.none(), Duration.ofSeconds(10))) {
            var service = service(source, runtime, null, "alice");
            var initial = draft(service);
            var definition = new ChainDefinition(1, "text_preparation", "java.lang.String", "java.lang.String",
                    List.of(new ChainDefinition.Operation("hold", "text.hold", Map.of())));
            var saved = service.saveDraft(initial.draft().id(), 1, definition);
            var pending = pool.submit(() -> service.test(saved.draft().id(), 2, UUID.randomUUID(), "private"));
            assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
            var snapshot = service.run(service.runs(saved.draft().id(), filter(), null, 10).items().get(0).id());
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (snapshot.status().equals("PREPARING") && System.nanoTime() < until) {
                Thread.sleep(10);
                snapshot = service.run(snapshot.id());
            }
            // When / Then
            assertThat(snapshot.status()).isEqualTo("RUNNING");
            assertThat(snapshot.observation().steps()).anyMatch(step -> step.status().equals("RUNNING"));
            assertThat(snapshot.observation().input().state()).isEqualTo(CapturedValue.State.NOT_CAPTURED);
            var recorder = new StudioRunRecorder(new JdbcStudioStore(source), "observations", Clock.systemUTC());
            assertThat(recorder.purgeTerminalBefore(Instant.now(), 100)).isZero();
            release.countDown();
            assertThat(pending.get(10, TimeUnit.SECONDS).state()).isEqualTo(TestSubmission.State.COMPLETED);
            assertThat(service.run(snapshot.id()).status()).isEqualTo("SUCCEEDED");
            // An application-created Java chain is observed without creating or exporting a
            // draft.
            var provenance = new StudioRun.Provenance("observations", "text_preparation", "build",
                    catalog.fingerprint(),
                    null, "application", "RUN", null, null, null, 0, null, null);
            var hostId = recorder.beginHostRun(initial.draft().chainId(), provenance);
            var observer = new Gear4jRunObserver(dispatcher, value -> recorder.record(hostId, value),
                    DataCapturePolicy.none(),
                    null);
            var line = AssemblyLines.<String>createAssemblyLine("text_preparation")
                    .then(Stations.processingOperation("hold", HoldOperation.class).build()).build();
            var result = executor
                    .execute(line,
                             RunRequest.builder().input("runtime").resourceFactory(resources).with(observer).build());
            recorder.record(hostId, observer.finish(result));
            assertThat(service.run(hostId).origin()).isEqualTo(StudioRun.Origin.HOST_RUN);
            assertThat(service.run(hostId).provenance().versionId()).isNull();
            assertThat(service.run(hostId).provenance().mode()).isEqualTo("RUN");
            var late = new RunObservation(UUID.randomUUID(), null, null, "RUNNING", Instant.now(), null,
                    CapturedValue.none(), CapturedValue.none(), null, List.of(), false, null);
            recorder.record(hostId, late);
            assertThat(service.run(hostId).status()).isEqualTo("SUCCEEDED");
            assertThat(recorder.purgeTerminalBefore(Instant.now(), 100)).isEqualTo(2);
            assertThat(service.runs(initial.draft().chainId(), filter(), null, 10).items()).isEmpty();
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }
}
