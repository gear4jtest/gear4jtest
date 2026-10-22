package io.github.gear4jtest.studio.jdbc;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import io.github.gear4jtest.jdbc.persistence.Gear4jDatabaseDialect;
import io.github.gear4jtest.studio.management.AccessGrant;
import io.github.gear4jtest.studio.management.AuditEvent;
import io.github.gear4jtest.studio.management.Permission;
import io.github.gear4jtest.studio.management.ResourceKind;
import io.github.gear4jtest.studio.management.StudioChain;
import io.github.gear4jtest.studio.management.StudioDomain;
import io.github.gear4jtest.studio.management.StudioDraft;
import io.github.gear4jtest.studio.model.ChainDefinition;
import io.github.gear4jtest.studio.model.DraftRevision;
import io.github.gear4jtest.studio.model.StudioPublication;
import io.github.gear4jtest.studio.model.StudioVersion;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
@Tag("docker")
@Testcontainers(disabledWithoutDocker = true)
class PostgreSqlStudioStoreIT {
    static final String RUNTIME_CONTEXT_ID = "context";
    @Container
    private static final PostgreSQLContainer<?> DATABASE = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void migrationsAtomicWritesAclHistoryAndOptimisticConflictsWorkOnPostgreSql() throws Exception {
        // Given
        var source = new PGSimpleDataSource();
        source.setURL(DATABASE.getJdbcUrl());
        source.setUser(DATABASE.getUsername());
        source.setPassword(DATABASE.getPassword());
        qualify(source);
    }

    static void qualify(javax.sql.DataSource source) throws Exception {
        StudioJdbcSchema.migrate(source, Gear4jDatabaseDialect.POSTGRESQL);
        StudioJdbcSchema.migrate(source, Gear4jDatabaseDialect.POSTGRESQL);
        var store = new JdbcStudioStore(source);
        store.bindContext(RUNTIME_CONTEXT_ID);
        var time = Instant.ofEpochMilli(1234);
        var domain = new StudioDomain(UUID.randomUUID(), "catalog", "Catalogue", "", "alice", 1, false, time, time);
        var chain = new StudioChain(UUID.randomUUID(), domain.id(), "vehicles", "runtime_vehicles", "Véhicules", "",
                "alice", 1, true,
                false, time, time);
        var draft = new StudioDraft(UUID.randomUUID(), chain.id(), "Draft", "alice", 1, false, false, 1,
                DraftRevision.Origin.STUDIO, null, time, time);
        var revision = new DraftRevision(draft.id(), UUID.randomUUID(), 1, "alice", time, DraftRevision.Origin.STUDIO,
                new ChainDefinition(1, "sample", "java.lang.String", "java.lang.String", List.of()));
        var grants = List.of(new AccessGrant(AccessGrant.SubjectType.GROUP, "reviewers", Set.of(Permission.READ)));
        String xml = "<assemblyLine>é\r\n";
        var raw = new DraftRevision(draft.id(), UUID.randomUUID(), 2, "alice", time, DraftRevision.Origin.STUDIO,
                DraftRevision.Kind.SOURCE, null, new DraftRevision.Source("application/xml", xml));
        String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var version = new StudioVersion(UUID.randomUUID(), chain.id(), draft.id(), revision.revisionId(), 1,
                chain.runtimeAssemblyLineId(), "1", "alice", time, RUNTIME_CONTEXT_ID, "build", "catalog",
                "application/xml",
                hash, xml);
        var publication = new StudioPublication(version.id(), StudioPublication.Mode.TEST,
                StudioPublication.State.REQUESTED,
                "alice", time, null, null, null, null);
        // When
        store.write(tx -> {
            tx.insert(domain);
            tx.lockDomain(domain.id());
            tx.insert(chain);
            tx.insert(draft);
            tx.appendRevision(revision);
            tx.appendRevision(raw);
            tx.insertVersion(version);
            tx.savePublication(publication);
            tx.replaceGrants(draft.id(), grants);
            tx.appendAudit(new AuditEvent(0, UUID.randomUUID(), domain.id(), chain.id(), draft.id(), ResourceKind.DRAFT,
                    draft.id(), 1, "alice", AuditEvent.Action.DRAFT_CREATED, time, revision.revisionId(), null, null,
                    null));
            return null;
        });
        // Then: new connection, repeatable-read snapshot and dialect-independent seek
        // queries.
        new JdbcStudioStore(source).read(tx -> {
            assertThat(tx.resource(draft.id())).isEqualTo(draft);
            assertThat(tx.revisions(draft.id(), 0, 10)).containsExactly(revision, raw);
            assertThat(tx.version(version.id())).isEqualTo(version);
            assertThat(tx.publication(version.id(), StudioPublication.Mode.TEST)).isEqualTo(publication);
            assertThat(tx.grants(draft.id())).containsExactlyElementsOf(grants);
            assertThat(tx.audit(domain.id(), chain.id(), draft.id(), 0, 10)).hasSize(1);
            assertThat(tx.chains(domain.id(), null, 10)).containsExactly(chain);
            return null;
        });
        var scenario = new io.github.gear4jtest.studio.model.StudioScenario(UUID.randomUUID(), chain.id(), chain.id(),
                "Fixture", "alice", 1, 1, false, time, time);
        var fixture = new io.github.gear4jtest.studio.model.ScenarioRevision(scenario.id(), UUID.randomUUID(), 1,
                "alice", time,
                new io.github.gear4jtest.studio.model.ScenarioData(new io.github.gear4jtest.studio.model.ScenarioInput(
                        io.github.gear4jtest.studio.model.ScenarioInput.Kind.FIXTURE_REFERENCE, "greeting", "1"),
                        null));
        store.write(tx -> {
            tx.lockDomain(domain.id());
            tx.insertScenario(scenario);
            tx.appendScenarioRevision(fixture);
            tx.appendAudit(new AuditEvent(0, UUID.randomUUID(), domain.id(), chain.id(), null, ResourceKind.CHAIN,
                    chain.id(), 1, "alice",
                    AuditEvent.Action.SCENARIO_CREATED, time, null, null, null, null, null, null, fixture.reference()));
            return null;
        });
        new JdbcStudioStore(source).read(tx -> {
            assertThat(tx.scenarios(chain.id(), null, 10)).containsExactly(scenario);
            assertThat(tx.scenarios(chain.id(), scenario.id().toString(), 10)).isEmpty();
            assertThat(tx.scenarioRevision(scenario.id(), 1)).isEqualTo(fixture);
            assertThat(tx.audit(domain.id(), chain.id(), null, 0, 10))
                    .anySatisfy(e -> assertThat(e.scenario()).isEqualTo(fixture.reference()));
            return null;
        });
        var recorder = new io.github.gear4jtest.studio.management.StudioRunRecorder(store, RUNTIME_CONTEXT_ID,
                java.time.Clock.systemUTC());
        var runId = recorder.beginHostRun(chain.id(), new io.github.gear4jtest.studio.model.StudioRun.Provenance(
                RUNTIME_CONTEXT_ID, chain.runtimeAssemblyLineId(), "build", "catalog", hash, "alice", "TEST", null,
                null, null,
                0, version.id(), version.label()));
        var observation = new io.github.gear4jtest.studio.model.RunObservation(UUID.randomUUID(), null, null,
                "SUCCEEDED", time, time.plusMillis(10), io.github.gear4jtest.studio.model.CapturedValue.none(),
                io.github.gear4jtest.studio.model.CapturedValue.none(), null, List.of(), false, hash);
        recorder.record(runId, observation);
        new JdbcStudioStore(source).read(tx -> {
            assertThat(tx.run(runId).observation()).isEqualTo(observation);
            var filter = new io.github.gear4jtest.studio.model.RunFilter(java.time.Instant.now().minusSeconds(60),
                    java.time.Instant.now().plusSeconds(60), "SUCCEEDED", "TEST", version.id(), observation.runId(),
                    10L);
            assertThat(tx.runs(chain.id(), null, filter, null, 10)).hasSize(1);
            return null;
        });
        assertThat(recorder.purgeTerminalBefore(time.plusMillis(11), 100)).isEqualTo(1);
        var orphan = recorder.beginHostRun(chain.id(), new io.github.gear4jtest.studio.model.StudioRun.Provenance(
                RUNTIME_CONTEXT_ID, chain.runtimeAssemblyLineId(), "build", "catalog", null, "host", "RUN", null, null,
                null, 0,
                null, null));
        var laterTime = Instant.now().plusSeconds(10);
        var maintenance = new io.github.gear4jtest.studio.management.StudioRunRecorder(store, RUNTIME_CONTEXT_ID,
                java.time.Clock.fixed(laterTime, java.time.ZoneOffset.UTC));
        assertThat(maintenance.markStaleBefore(laterTime.minusSeconds(1), 100)).isEqualTo(1);
        store.read(tx -> {
            assertThat(tx.run(orphan).status()).isEqualTo("PREPARING");
            assertThat(tx.run(orphan).staleAt()).isEqualTo(Instant.ofEpochMilli(laterTime.toEpochMilli()));
            return null;
        });
        var retention = new io.github.gear4jtest.studio.management.StudioRunRecorder(store, RUNTIME_CONTEXT_ID,
                java.time.Clock.fixed(laterTime.plusSeconds(2), java.time.ZoneOffset.UTC));
        assertThat(retention.purgeStaleBefore(laterTime.plusSeconds(1), 100)).isEqualTo(1);
        // Leave a populated run and simulation in the baseline for native
        // backup/restore.
        var retained = recorder.beginHostRun(chain.id(), new io.github.gear4jtest.studio.model.StudioRun.Provenance(
                RUNTIME_CONTEXT_ID, chain.runtimeAssemblyLineId(), "build", "catalog", hash, "alice", "TEST", null,
                null, null,
                0, version.id(), version.label()));
        recorder.record(retained, observation);
        var simulated = new io.github.gear4jtest.studio.model.StudioSimulation(UUID.randomUUID(), chain.id(),
                chain.id(), "alice", time, RUNTIME_CONTEXT_ID, "build", fixture.reference(),
                new io.github.gear4jtest.studio.model.ScenarioTarget(null, null, version.id()), revision.revisionId(),
                1, hash,
                new io.github.gear4jtest.studio.model.SimulationResult("storage-fixture", "catalog", "SUCCEEDED",
                        null, io.github.gear4jtest.studio.model.CapturedValue.none(),
                        io.github.gear4jtest.studio.model.CapturedValue.none(), List.of(), null),
                List.of());
        store.write(tx -> {
            tx.lockDomain(domain.id());
            tx.insertSimulation(simulated);
            return null;
        });
        new JdbcStudioStore(source).read(tx -> {
            assertThat(tx.simulation(simulated.id())).isEqualTo(simulated);
            assertThat(tx.run(retained).observation()).isEqualTo(observation);
            return null;
        });
        var changed = new StudioDomain(domain.id(), domain.key(), "Changed", "", "alice", 2, false, time, time);
        store.write(tx -> {
            tx.lockDomain(domain.id());
            tx.update(changed, 1);
            return null;
        });
        assertThatThrownBy(() -> store.write(tx -> {
            tx.lockDomain(domain.id());
            tx.update(changed, 1);
            return null;
        })).hasMessage("CONFLICT");
        assertThatThrownBy(() -> store.write(tx -> {
            tx.lockDomain(domain.id());
            tx.replaceGrants(draft.id(), List.of());
            throw new IllegalStateException("rollback");
        })).hasMessage("rollback");
        store.read(tx -> {
            assertThat(tx.grants(draft.id())).containsExactlyElementsOf(grants);
            return null;
        });
    }
}
