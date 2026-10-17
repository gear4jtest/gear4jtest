package io.github.gear4jtest.studio.jdbc;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import io.github.gear4jtest.jdbc.persistence.Gear4jDatabaseDialect;
import io.github.gear4jtest.studio.management.*;
import io.github.gear4jtest.studio.model.*;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.*;

class JdbcStudioStoreTest {
    @TempDir
    Path directory;

    private JdbcDataSource source() {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:file:" + directory.resolve("studio") + ";DB_CLOSE_ON_EXIT=FALSE");
        return ds;
    }

    @Test
    void revisionsAclAuditAndMetadataSurviveReopeningTheDatabase() throws Exception {
        // Given
        var ds = source();
        StudioJdbcSchema.migrate(ds, Gear4jDatabaseDialect.H2);
        StudioJdbcSchema.migrate(ds, Gear4jDatabaseDialect.H2);
        var store = new JdbcStudioStore(ds);
        var time = Instant.ofEpochMilli(123456);
        var domain = new StudioDomain(UUID.randomUUID(), "domain", "Domaine", "Description", "alice", 1, false, time,
                time);
        var chain = new StudioChain(UUID.randomUUID(), domain.id(), "chain", "runtime_chain", "Chaîne", "Description",
                "alice", 1, true,
                false, time, time);
        var draft = new StudioDraft(UUID.randomUUID(), chain.id(), "Draft", "alice", 1, false, false, 1,
                DraftRevision.Origin.STUDIO, null, time, time);
        var definition = new ChainDefinition(1, "sample", "java.lang.String", "java.lang.String",
                List.of(new ChainDefinition.Operation("step", "op",
                        Map.of("text", new ParameterValue(ParameterValue.Kind.TEXT, "é\t\n<&>")))));
        var revision = new DraftRevision(draft.id(), UUID.randomUUID(), 1, "alice", time, DraftRevision.Origin.STUDIO,
                definition);
        var policy = new AccessPolicy(true, List
                .of(new AccessGrant(AccessGrant.SubjectType.GROUP, "team", Set.of(Permission.READ, Permission.TEST))));
        // When
        store.write(tx -> {
            tx.insert(domain);
            tx.lockDomain(domain.id());
            tx.insert(chain);
            tx.insert(draft);
            tx.appendRevision(revision);
            tx.replaceGrants(chain.id(), policy.grants());
            tx.appendAudit(new AuditEvent(0, UUID.randomUUID(), domain.id(), chain.id(), draft.id(), ResourceKind.DRAFT,
                    draft.id(), 1, "alice", AuditEvent.Action.DRAFT_CREATED, time, revision.revisionId(), null, null,
                    policy));
            return null;
        });
        var raw = new DraftRevision(draft.id(), UUID.randomUUID(), 2, "alice", time, DraftRevision.Origin.STUDIO,
                DraftRevision.Kind.SOURCE, null,
                new DraftRevision.Source("application/xml", "<assemblyLine>\r\n  <operations>\n"));
        store.write(tx -> {
            tx.lockDomain(domain.id());
            tx.appendRevision(raw);
            return null;
        });
        String artifact = "<assemblyLine>é\r\n</assemblyLine>";
        String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(artifact.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var version = new StudioVersion(UUID.randomUUID(), chain.id(), draft.id(), revision.revisionId(), 1,
                chain.runtimeAssemblyLineId(), "1.0", "alice", time, "context", "build", "catalog", "application/xml",
                hash, artifact);
        var publication = new StudioPublication(version.id(), StudioPublication.Mode.TEST,
                StudioPublication.State.CONFIRMED,
                "alice", time, time, "alice", time, null);
        store.write(tx -> {
            tx.lockDomain(domain.id());
            tx.insertVersion(version);
            tx.savePublication(publication);
            return null;
        });
        var reopened = new JdbcStudioStore(source());
        // Then
        reopened.read(tx -> {
            assertThat(tx.version(version.id())).isEqualTo(version);
            assertThat(tx.publication(version.id(), StudioPublication.Mode.TEST)).isEqualTo(publication);
            assertThat(tx.versions(chain.id(), null, 10)).containsExactly(version);
            assertThat(tx.resource(domain.id())).isEqualTo(domain);
            assertThat(tx.resource(chain.id())).isEqualTo(chain);
            assertThat(tx.resource(draft.id())).isEqualTo(draft);
            assertThat(tx.revision(draft.id(), 1)).isEqualTo(revision);
            assertThat(tx.revision(draft.id(), 2)).isEqualTo(raw);
            assertThat(tx.grants(chain.id())).containsExactlyElementsOf(policy.grants());
            assertThat(tx.domains(null, 10)).containsExactly(domain);
            assertThat(tx.chains(domain.id(), null, 10)).containsExactly(chain);
            assertThat(tx.drafts(chain.id(), null, 10)).containsExactly(draft);
            assertThat(tx.audit(domain.id(), chain.id(), null, 0, 10)).singleElement()
                    .satisfies(e -> assertThat(e.accessAfter()).isEqualTo(policy));
            return null;
        });
        store.write(tx -> {
            tx.lockDomain(domain.id());
            tx.update(new StudioChain(chain.id(), domain.id(), chain.key(), chain.runtimeAssemblyLineId(), "Updated",
                    chain.description(),
                    chain.owner(), 2, false, true, time, time), 1);
            tx.replaceGrants(chain.id(), List.of());
            return null;
        });
        boolean archived = reopened.read(tx -> ((StudioChain) tx.resource(chain.id())).archived());
        assertThat(archived).isTrue();
        List<AccessGrant> grants = reopened.read(tx -> tx.grants(chain.id()));
        assertThat(grants).isEmpty();
        // Then: the CASE-based enum constraint still rejects invalid kinds after
        // reopening.
        try (var connection = source().getConnection();
                var statement = connection.prepareStatement("UPDATE g4js_resource SET kind='UNKNOWN' WHERE id=?")) {
            statement.setString(1, domain.id().toString());
            assertThatThrownBy(statement::executeUpdate).isInstanceOf(java.sql.SQLException.class);
        }
        // Corrupt definitions are never returned as trusted authoring state.
        try (var connection = source().getConnection();
                var statement = connection.prepareStatement("UPDATE g4js_revision SET definition_hash=? WHERE id=?")) {
            statement.setString(1, "0".repeat(64));
            statement.setString(2, revision.revisionId().toString());
            statement.executeUpdate();
        }
        assertThatThrownBy(() -> reopened.read(tx -> tx.revision(draft.id(), 1))).hasMessage("STORAGE_UNAVAILABLE");
        try (var connection = source().getConnection();
                var statement = connection.prepareStatement("UPDATE g4js_version SET source_text=? WHERE id=?")) {
            statement.setString(1, "<different/>");
            statement.setString(2, version.id().toString());
            statement.executeUpdate();
        }
        assertThatThrownBy(() -> reopened.read(tx -> tx.version(version.id()))).hasMessage("STORAGE_UNAVAILABLE");

    }

    @Test
    void scenarioHistoryIsImmutableAndCorruptFixtureDataFailsClosed() throws Exception {
        // Given
        var ds = source();
        StudioJdbcSchema.migrate(ds, Gear4jDatabaseDialect.H2);
        var store = new JdbcStudioStore(ds);
        var time = Instant.ofEpochMilli(12345);
        var domain = new StudioDomain(UUID.randomUUID(), "d", "D", "", "alice", 1, false, time, time);
        var chain = new StudioChain(UUID.randomUUID(), domain.id(), "c", "sample", "C", "", "alice", 1, true, false,
                time, time);
        var scenario = new StudioScenario(UUID.randomUUID(), chain.id(), chain.id(), "Greeting", "alice", 1, 1, false,
                time, time);
        var data = new ScenarioData(new ScenarioInput(ScenarioInput.Kind.FIXTURE_REFERENCE, "demo/greeting", "1"),
                new ScenarioExpectation("SUCCEEDED", "HELLO"));
        var revision = new ScenarioRevision(scenario.id(), UUID.randomUUID(), 1, "alice", time, data);
        store.write(tx -> {
            tx.insert(domain);
            tx.lockDomain(domain.id());
            tx.insert(chain);
            tx.insertScenario(scenario);
            tx.appendScenarioRevision(revision);
            tx.appendAudit(new AuditEvent(0, UUID.randomUUID(), domain.id(), chain.id(), null, ResourceKind.CHAIN,
                    chain.id(), 1, "alice", AuditEvent.Action.SCENARIO_CREATED, time, null, null, null, null, null,
                    null, revision.reference()));
            return null;
        });
        // When / Then
        var reopened = new JdbcStudioStore(source());
        reopened.read(tx -> {
            assertThat(tx.scenario(scenario.id())).isEqualTo(scenario);
            assertThat(tx.scenarioRevision(scenario.id(), 1)).isEqualTo(revision);
            assertThat(tx.scenarioRevisions(scenario.id(), 0, 10)).containsExactly(revision);
            assertThat(tx.scenarioRevisions(scenario.id(), 1, 10)).isEmpty();
            assertThat(tx.scenarios(chain.id(), null, 10)).containsExactly(scenario);
            assertThat(tx.scenarios(chain.id(), scenario.id().toString(), 10)).isEmpty();
            assertThat(tx.audit(domain.id(), chain.id(), null, 0, 10)).singleElement()
                    .satisfies(e -> assertThat(e.scenario()).isEqualTo(revision.reference()));
            return null;
        });
        var archived = new StudioScenario(scenario.id(), chain.id(), chain.id(), "Renamed", "alice", 2, 1, true, time,
                time);
        store.write(tx -> {
            tx.lockDomain(domain.id());
            tx.updateScenario(archived, 1);
            return null;
        });
        assertThatThrownBy(() -> store.write(tx -> {
            tx.updateScenario(scenario, 1);
            return null;
        })).hasMessage("CONFLICT");
        assertThatThrownBy(() -> store.write(tx -> {
            tx.appendScenarioRevision(revision);
            return null;
        })).hasMessage("CONFLICT");
        try (var c = ds.getConnection();
                var statement = c.prepareStatement("UPDATE g4js_scenario_revision SET data_json=? WHERE id=?")) {
            statement.setString(1, "{}");
            statement.setString(2, revision.id().toString());
            statement.executeUpdate();
        }
        assertThatThrownBy(() -> reopened.read(tx -> tx.scenarioRevision(scenario.id(), 1)))
                .hasMessage("STORAGE_UNAVAILABLE");
    }

    @Test
    void fatalFailuresRollBackAndSessionHandlesCannotEscapeTheirTransaction() {
        // Given
        var ds = source();
        StudioJdbcSchema.migrate(ds, Gear4jDatabaseDialect.H2);
        var store = new JdbcStudioStore(ds);
        var domain = new StudioDomain(UUID.randomUUID(), "rollback", "Rollback", "", "alice", 1, false, Instant.EPOCH,
                Instant.EPOCH);
        // When
        assertThatThrownBy(() -> store.write(tx -> {
            tx.insert(domain);
            throw new AssertionError("fatal");
        })).isInstanceOf(AssertionError.class);
        // Then
        ManagedResource missing = store.read(tx -> tx.resource(domain.id()));
        assertThat(missing).isNull();
        var escaped = new AtomicReference<StudioStore.Session>();
        store.read(tx -> {
            escaped.set(tx);
            return null;
        });
        assertThatThrownBy(() -> escaped.get().domains(null, 10)).hasMessage("Expired Studio transaction");
        assertThatThrownBy(() -> store.read(tx -> {
            tx.insert(domain);
            return null;
        })).hasMessage("Read-only Studio transaction");
        assertThatThrownBy(() -> StudioJdbcSchema.migrator(Gear4jDatabaseDialect.MYSQL))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
