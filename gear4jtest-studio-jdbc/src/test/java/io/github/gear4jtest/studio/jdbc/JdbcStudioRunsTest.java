package io.github.gear4jtest.studio.jdbc;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import io.github.gear4jtest.jdbc.persistence.Gear4jDatabaseDialect;
import io.github.gear4jtest.studio.management.*;
import io.github.gear4jtest.studio.model.*;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.*;

class JdbcStudioRunsTest {
    @TempDir
    Path directory;
    private final Instant start = Instant.parse("2026-10-04T10:00:00Z");
    private final UUID chainId = UUID.randomUUID();
    private final UUID engineId = UUID.randomUUID();
    private JdbcDataSource source;
    private JdbcStudioStore store;
    private StudioRunRecorder recorder;

    @BeforeEach
    void setup() {
        source = new JdbcDataSource();
        source.setURL("jdbc:h2:file:" + directory.resolve("runs"));
        StudioJdbcSchema.migrate(source, Gear4jDatabaseDialect.H2);
        store = new JdbcStudioStore(source);
        recorder = new StudioRunRecorder(store, "runtime", Clock.fixed(start.plusSeconds(3), ZoneOffset.UTC));
        var domain = new StudioDomain(UUID.randomUUID(), "domain", "Domain", "", "alice", 1, false, start, start);
        var chain = new StudioChain(chainId, domain.id(), "chain", "runtime_chain", "Chain", "", "alice", 1, true,
                false, start, start);
        store.write(tx -> {
            tx.insert(domain);
            tx.lockDomain(domain.id());
            tx.insert(chain);
            return null;
        });
    }

    private UUID begin() {
        return recorder.beginHostRun(chainId, new StudioRun.Provenance("runtime", "runtime_chain", "build", "catalog",
                "a".repeat(64), "application", "RUN", null, null, null, 0, null, null));
    }

    private RunObservation snapshot(String status) {
        return new RunObservation(engineId, null, engineId, status, start,
                status.equals("RUNNING") ? null : start.plusSeconds(1),
                new CapturedValue(CapturedValue.State.REDACTED, "[redacted]"), CapturedValue.none(), null,
                List.of(new ControlledRun.Step(UUID.randomUUID(), "step", status, start, start.plusSeconds(1))), false,
                "a".repeat(64));
    }

    @Test
    void journalSurvivesReopeningFiltersBySqlAndPurgesOnlyFinishedRuns() {
        // Given
        UUID finished = begin();
        UUID active = begin();
        recorder.record(finished, snapshot("RUNNING"));
        recorder.record(finished, snapshot("SUCCEEDED"));
        recorder.record(active, snapshot("RUNNING"));
        var reopened = new JdbcStudioStore(source);
        var filter = new RunFilter(start, start.plusSeconds(10), "SUCCEEDED", "RUN", null, engineId, 1000L);
        // When / Then
        reopened.read(tx -> {
            var run = tx.run(finished);
            assertThat(run.sequence()).isEqualTo(2);
            assertThat(run.observation().input().state()).isEqualTo(CapturedValue.State.REDACTED);
            assertThat(tx.runs(chainId, null, filter, null, 10)).extracting(StudioRun::id).containsExactly(finished);
            assertThat(tx.runs(chainId, null, filter, run.createdAt().toEpochMilli() + ":" + run.id(), 10)).isEmpty();
            assertThat(tx.runs(chainId, chainId, filter, null, 10)).hasSize(1);
            assertThat(tx.runs(chainId, null,
                               new RunFilter(filter.from(), filter.until(), null, null, UUID.randomUUID(), null, null),
                               null, 10))
                    .isEmpty();
            return null;
        });
        assertThatThrownBy(() -> recorder.record(active,
                                                 new RunObservation(UUID.randomUUID(), null, null, "RUNNING", start,
                                                         null, CapturedValue.none(), CapturedValue.none(), null,
                                                         List.of(), false, "a".repeat(64))))
                .hasMessage("Observation provenance changed");
        assertThat(recorder.purgeTerminalBefore(start.plusSeconds(2), 1)).isEqualTo(1);
        assertThat(recorder.purgeTerminalBefore(start.plusSeconds(2), 100)).isZero();
        reopened.read(tx -> {
            assertThat(tx.run(finished)).isNull();
            assertThat(tx.run(active).status()).isEqualTo("RUNNING");
            return null;
        });
    }

    @Test
    void staleIsFreshnessOnlyAndLateObservationClearsItBeforeExplicitRetention() {
        // Given
        UUID waiting = begin();
        UUID running = begin();
        UUID finished = begin();
        recorder.record(running, snapshot("RUNNING"));
        recorder.record(finished, snapshot("SUCCEEDED"));
        var maintenance = new StudioRunRecorder(store, "runtime", Clock.fixed(start.plusSeconds(60), ZoneOffset.UTC));
        // When / Then: bounded maintenance does not create an outcome or an end time.
        assertThat(maintenance.markStaleBefore(start.plusSeconds(10), 1)).isEqualTo(1);
        assertThat(maintenance.markStaleBefore(start.plusSeconds(10), 10)).isEqualTo(1);
        assertThat(maintenance.markStaleBefore(start.plusSeconds(10), 10)).isZero();
        store.read(tx -> {
            assertThat(tx.run(waiting).status()).isEqualTo("PREPARING");
            assertThat(tx.run(running).status()).isEqualTo("RUNNING");
            assertThat(tx.run(running).endedAt()).isNull();
            assertThat(tx.run(running).updatedAt()).isEqualTo(start.plusSeconds(3));
            assertThat(tx.run(running).staleAt()).isEqualTo(start.plusSeconds(60));
            assertThat(tx.run(finished).staleAt()).isNull();
            return null;
        });
        maintenance.record(running, snapshot("SUCCEEDED"));
        var later = new StudioRunRecorder(store, "runtime", Clock.fixed(start.plusSeconds(120), ZoneOffset.UTC));
        assertThat(later.purgeStaleBefore(start.plusSeconds(59), 10)).isZero();
        assertThat(later.purgeStaleBefore(start.plusSeconds(61), 10)).isEqualTo(1);
        store.read(tx -> {
            assertThat(tx.run(waiting)).isNull();
            assertThat(tx.run(running).staleAt()).isNull();
            assertThat(tx.run(running).status()).isEqualTo("SUCCEEDED");
            return null;
        });
        assertThatThrownBy(() -> maintenance.markStaleBefore(start.plusSeconds(61), 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void independentRunUpdatesDoNotWaitForDomainOrAnotherRunRowLock() throws Exception {
        // Given
        UUID held = begin();
        UUID independent = begin();
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        var locked = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var holding = pool.submit(() -> store.write(tx -> {
            var chain = (StudioChain) tx.resource(chainId);
            tx.lockDomain(chain.domainId());
            tx.lockRun(held);
            locked.countDown();
            try {
                if (!release.await(10, java.util.concurrent.TimeUnit.SECONDS))
                    throw new IllegalStateException("Synthetic deadline");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            return null;
        }));
        try {
            assertThat(locked.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            // When / Then
            pool.submit(() -> recorder.record(independent, snapshot("SUCCEEDED")))
                    .get(3, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(release.getCount()).isEqualTo(1);
            store.read(tx -> {
                assertThat(tx.run(independent).status()).isEqualTo("SUCCEEDED");
                return null;
            });
        } finally {
            release.countDown();
            holding.get(5, java.util.concurrent.TimeUnit.SECONDS);
            pool.shutdownNow();
        }
    }

    @Test
    void contextGuardRollbackAndCorruptSnapshotFailClosed() throws Exception {
        // Given
        UUID id = begin();
        var observation = snapshot("SUCCEEDED");
        assertThatThrownBy(() -> new StudioRunRecorder(store, "different", Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class);
        // When: a transaction loses its outcome before commit.
        assertThatThrownBy(() -> store.write(tx -> {
            var before = tx.run(id);
            tx.saveRun(new StudioRun(id, before.resourceId(), before.chainId(), before.origin(), before.provenance(),
                    "SUCCEEDED", before.createdAt(), start, start, start.plusSeconds(1), 1, observation));
            throw new IllegalStateException("rollback");
        })).hasMessage("rollback");
        store.read(tx -> {
            assertThat(tx.run(id).status()).isEqualTo("PREPARING");
            return null;
        });
        // Then: malformed stored data is not returned as a trusted snapshot.
        try (var connection = source.getConnection();
                var statement = connection.prepareStatement("UPDATE g4js_run SET snapshot_json=? WHERE id=?")) {
            statement.setString(1, "{broken");
            statement.setString(2, id.toString());
            statement.executeUpdate();
        }
        assertThatThrownBy(() -> store.read(tx -> tx.run(id))).hasMessage("STORAGE_UNAVAILABLE");
    }
}
