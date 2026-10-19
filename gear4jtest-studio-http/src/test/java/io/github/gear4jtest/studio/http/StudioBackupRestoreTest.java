package io.github.gear4jtest.studio.http;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import io.github.gear4jtest.jdbc.migration.SchemaMigrationException;
import io.github.gear4jtest.jdbc.persistence.Gear4jDatabaseDialect;
import io.github.gear4jtest.studio.demo.*;
import io.github.gear4jtest.studio.jdbc.*;
import io.github.gear4jtest.studio.management.*;
import io.github.gear4jtest.studio.model.*;
import io.github.gear4jtest.studio.spi.DataCapturePolicy;
import org.h2.jdbcx.JdbcDataSource;
import org.h2.tools.Restore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.*;

class StudioBackupRestoreTest {
    @TempDir
    Path directory;

    @Test
    void restoresPopulatedBaselineWithoutLosingDataOrRebindingContext() throws Exception {
        // Given: real file database, durable objects and runtime.
        var db = source("original");
        var resources = DemoWiring.plainResources();
        var identity = new StudioIdentity("alice", Set.of(), false);
        try (var runtime = DemoWiring.runtime("restore", DemoWiring.plainExecutor(resources), resources,
                                              DataCapturePolicy.allowed());
                var publisher = new DemoPublicationHost(db, DemoWiring.catalog())) {
            StudioJdbcSchema.migrate(db, Gear4jDatabaseDialect.H2);
            var service = new StudioManagementService(new JdbcStudioStore(db), runtime, () -> identity,
                    a -> true, Clock.systemUTC(), publisher.publisher(), TestReceiptPolicy.defaults(),
                    new DemoScenarioPolicy());
            var domain = service.createDomain("restore", "Restore", "").resource();
            var chain = service.createChain(domain.id(), "chain", "Chain", "", "text_preparation").resource();
            var draft = service.createDraft(chain.id(), "Draft", DemoWiring.sample());
            var grants = new AccessPolicy(true, List.of(new AccessGrant(AccessGrant.SubjectType.USER, "reader",
                    Set.of(Permission.READ, Permission.READ_SCENARIOS, Permission.READ_SIMULATIONS))));
            service.setAccess(chain.id(), 1, grants);
            var scenario = service.createScenario(chain.id(), "Synthetic", new ScenarioData(
                    new ScenarioInput(ScenarioInput.Kind.SYNTHETIC_TEXT, "  hello  ", null), null,
                    new SimulationProfile(1,
                            Map.of("trim",
                                   new SimulationProfile.Mock("text.trim", SimulationProfile.Behavior.PASSTHROUGH, null,
                                           null),
                                   "uppercase", new SimulationProfile.Mock("text.uppercase",
                                           SimulationProfile.Behavior.PASSTHROUGH, null, null)))));
            var version = service.createVersion(draft.draft().id(), 1, "1.0.0");
            service.publishTest(version.version().id());
            service.promoteRun(version.version().id());
            var target = new ScenarioTarget(null, null, version.version().id());
            var report = service.simulate(scenario.scenario().id(), 1, target, UUID.randomUUID());
            var test = service.testScenario(scenario.scenario().id(), 1, target, UUID.randomUUID());
            assertThat(test.state().name()).isEqualTo("COMPLETED");
            var raw = service.saveSource(draft.draft().id(), 1,
                                         new DraftRevision.Source("application/xml",
                                                 "<assemblyLine id=\"text_preparation\">\r\n"));
            var runs = service.runs(chain
                    .id(), new RunFilter(Instant.now().minusSeconds(60), Instant.now().plusSeconds(60),
                            null, null, null, null, null), null, 25);
            assertThat(runs.items()).hasSize(1);
            var observed = service.run(runs.items().get(0).id());
            // When: native backup, then restore to a separate file.
            Path backup = directory.resolve("backup.zip");
            try (var connection = db.getConnection(); var statement = connection.createStatement()) {
                statement.execute("BACKUP TO '" + backup.toString().replace("'", "''") + "'");
            }
            Restore.execute(backup.toString(), directory.toString(), "restored");
            var restored = source("restored");
            StudioJdbcSchema.migrate(restored, Gear4jDatabaseDialect.H2);
            var store = new JdbcStudioStore(restored);
            var reopened = new StudioManagementService(store, runtime, () -> identity, a -> true, Clock.systemUTC(),
                    null, TestReceiptPolicy.defaults(), new DemoScenarioPolicy());
            // Then
            assertThat(reopened.draft(draft.draft().id()).revision()).isEqualTo(raw.revision());
            assertThat(reopened.scenario(scenario.scenario().id()).revision()).isEqualTo(scenario.revision());
            assertThat(reopened.simulation(report.id())).isEqualTo(report);
            assertThat(reopened.version(version.version().id()).version()).isEqualTo(version.version());
            assertThat(reopened.run(observed.id())).isEqualTo(observed);
            store.read(tx -> {
                assertThat(tx.grants(chain.id())).containsExactlyElementsOf(grants.grants());
                return null;
            });
            assertThatThrownBy(() -> store.bindContext("another.context")).isInstanceOf(IllegalStateException.class);
            try (var connection = restored.getConnection(); var statement = connection.createStatement()) {
                statement
                        .executeUpdate("UPDATE gear4j_schema_history SET checksum='old-prototype' WHERE module_id='gear4j-studio'");
            }
            assertThatThrownBy(() -> StudioJdbcSchema.migrate(restored, Gear4jDatabaseDialect.H2))
                    .isInstanceOf(SchemaMigrationException.class).hasMessageContaining("Checksum mismatch");
            assertThat(reopened.simulation(report.id())).isEqualTo(report);
        }
    }

    private JdbcDataSource source(String name) {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:file:" + directory.resolve(name) + ";DB_CLOSE_ON_EXIT=FALSE");
        return source;
    }
}
