package io.github.gear4jtest.studio.demo;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import io.github.gear4jtest.jdbc.persistence.Gear4jDatabaseDialect;
import io.github.gear4jtest.studio.jdbc.JdbcStudioStore;
import io.github.gear4jtest.studio.jdbc.StudioJdbcSchema;
import io.github.gear4jtest.studio.management.*;
import io.github.gear4jtest.studio.model.*;
import io.github.gear4jtest.studio.spi.DataCapturePolicy;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import static io.github.gear4jtest.studio.management.Permission.*;
import static org.assertj.core.api.Assertions.*;

class StudioRunQueriesTest {
    private JdbcDataSource source() {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + UUID.randomUUID());
        return source;
    }

    private static StudioRun.Provenance provenance(String context, UUID draft) {
        return new StudioRun.Provenance(context, "text_preparation", "build", null, null, "host", "RUN", null,
                draft, null, 0, null, null);
    }

    @Test
    void aclScanBudgetUsesOpaqueBoundCursorsAndSurvivesRemovalOfCursorRows() throws Exception {
        // Given: 500 recent private observations in front of one older shared run.
        var source = source();
        var resources = DemoWiring.plainResources();
        try (var lifetime = source.getConnection();
                var runtime = DemoWiring.runtime("queries", DemoWiring.plainExecutor(resources), resources,
                                                 DataCapturePolicy.none())) {
            StudioJdbcSchema.migrate(source, Gear4jDatabaseDialect.H2);
            var store = new JdbcStudioStore(source);
            var alice = new StudioManagementService(store, runtime, () -> new StudioIdentity("alice", Set.of(), false),
                    a -> true);
            var bob = new StudioManagementService(store, runtime, () -> new StudioIdentity("bob", Set.of(), false),
                    a -> true);
            var domain = alice.createDomain("d", "D", "").resource();
            var chain = alice.createChain(domain.id(), "c", "C", "", "text_preparation").resource();
            var draft = alice.createDraft(chain.id(), "Private", DemoWiring.sample()).draft();
            alice.setAccess(chain.id(), 1, new AccessPolicy(true, List.of(new AccessGrant(AccessGrant.SubjectType.USER,
                    "bob", Set.of(READ, READ_RUNS, READ_OBSERVABILITY)))));
            Instant base = Instant.ofEpochMilli(System.currentTimeMillis());
            var filter = new RunFilter(base.minusSeconds(1), base.plusSeconds(10), null, null, null, null, null);
            UUID shared = UUID.randomUUID();
            store.write(tx -> {
                tx.lockDomain(domain.id());
                tx.saveRun(new StudioRun(shared, chain.id(), chain.id(), StudioRun.Origin.HOST_RUN,
                        provenance(runtime.context().id(), null), "PREPARING", base, base, null, null, 0, null));
                for (int i = 1; i <= 500; i++) {
                    var time = base.plusMillis(i);
                    tx.saveRun(new StudioRun(UUID.randomUUID(), draft.id(), chain.id(),
                            StudioRun.Origin.CONTROLLED_TEST,
                            provenance(runtime.context().id(), draft.id()), "PREPARING", time, time, null, null, 0,
                            null));
                }
                return null;
            });
            // When / Then: empty page with continuation, no total or private identity
            // exposed.
            var page = bob.runs(chain.id(), filter, null, 1);
            assertThat(page.items()).isEmpty();
            assertThat(page.nextCursor()).isNotNull().doesNotContain(draft.id().toString());
            assertThat(bob.metrics(chain.id(), filter).total()).isZero();
            assertThat(bob.metrics(chain.id(), filter).partial()).isTrue();
            assertThatThrownBy(() -> alice.runs(chain.id(), filter, page.nextCursor(), 1))
                    .hasMessage("INVALID_REQUEST");
            var changedFilter = new RunFilter(filter.from(), filter.until(), "RUNNING", null, null, null, null);
            assertThatThrownBy(() -> bob.runs(chain.id(), changedFilter, page.nextCursor(), 1))
                    .hasMessage("INVALID_REQUEST");
            try (var statement = lifetime.prepareStatement("DELETE FROM g4js_run WHERE resource_id=?")) {
                statement.setString(1, draft.id().toString());
                statement.executeUpdate();
            }
            var next = bob.runs(chain.id(), filter, page.nextCursor(), 1);
            assertThat(next.items()).extracting(StudioRun::id).containsExactly(shared);
            assertThat(next.nextCursor()).isNull();
        }
    }

    @Test
    void metricsIncludeSlowAndFailingStepsAndDeclareTruncatedSamples() throws Exception {
        // Given
        var source = source();
        var resources = DemoWiring.plainResources();
        try (var lifetime = source.getConnection();
                var runtime = DemoWiring.runtime("metrics", DemoWiring.plainExecutor(resources), resources,
                                                 DataCapturePolicy.none())) {
            StudioJdbcSchema.migrate(source, Gear4jDatabaseDialect.H2);
            var store = new JdbcStudioStore(source);
            var service = new StudioManagementService(store, runtime,
                    () -> new StudioIdentity("alice", Set.of(), false), a -> true);
            var domain = service.createDomain("d", "D", "").resource();
            var chain = service.createChain(domain.id(), "c", "C", "", "text_preparation").resource();
            var recorder = new StudioRunRecorder(store, runtime.context().id(), Clock.systemUTC());
            var provenance = provenance(runtime.context().id(), null);
            var start = Instant.now().minusSeconds(10);
            for (int i = 1; i <= 3; i++) {
                UUID id = recorder.beginHostRun(chain.id(), provenance);
                var steps = new ArrayList<ControlledRun.Step>();
                for (int j = 0; j < 21; j++)
                    steps.add(new ControlledRun.Step(UUID.randomUUID(), "slow" + j, "SUCCEEDED", start,
                            start.plusMillis(300 + j)));
                steps.add(new ControlledRun.Step(UUID.randomUUID(), "fast-failure", "FAILED", start,
                        start.plusMillis(1)));
                recorder.record(id, new RunObservation(UUID.randomUUID(), null, null, i == 3 ? "FAILED" : "SUCCEEDED",
                        start, start.plusMillis(100L * i), CapturedValue.none(), CapturedValue.none(),
                        i == 3 ? "EXECUTION_ERROR" : null,
                        steps, i == 3, null));
            }
            recorder.beginHostRun(chain.id(), provenance);
            // When / Then
            var filter = new RunFilter(start, Instant.now().plusSeconds(1), null, "RUN", null, null, null);
            var metrics = service.metrics(chain.id(), filter);
            assertThat(metrics.total()).isEqualTo(4);
            assertThat(metrics.statuses()).containsEntry("SUCCEEDED", 2L).containsEntry("FAILED", 1L)
                    .containsEntry("PREPARING", 1L);
            assertThat(metrics.minimumMillis()).isEqualTo(100);
            assertThat(metrics.averageMillis()).isEqualTo(200);
            assertThat(metrics.maximumMillis()).isEqualTo(300);
            assertThat(metrics.p95Millis()).isEqualTo(300);
            assertThat(metrics.timeline().stream().mapToLong(RunMetrics.Bucket::runs).sum()).isEqualTo(4);
            assertThat(metrics.operations()).filteredOn(o -> o.nodeId().equals("fast-failure")).singleElement()
                    .satisfies(o -> assertThat(o.failures()).isEqualTo(3));
            assertThat(metrics.partial()).isTrue();
            assertThatThrownBy(() -> recorder.beginHostRun(chain.id(), provenance("other-context", null)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new RunObservation(null, null, null, "FAILED", start, start,
                    CapturedValue.none(), CapturedValue.none(), "provider raw secret", List.of(), false, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
