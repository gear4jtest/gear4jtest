package io.github.gear4jtest.jdbc.execution;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.gear4jtest.core.api.context.PayloadCloner;
import io.github.gear4jtest.core.model.StationLogStatus;
import io.github.gear4jtest.core.persistence.AssemblyRunRecord;
import io.github.gear4jtest.core.persistence.PersistenceRuntimeStats;
import io.github.gear4jtest.core.persistence.StationLogRecord;
import io.github.gear4jtest.jdbc.persistence.DatabaseAssemblyRunRepository;
import io.github.gear4jtest.jdbc.persistence.PersistenceJsonCodec;

import static org.assertj.core.api.Assertions.assertThat;

final class DatabaseExecutionManagerTestFixture {
    private DatabaseExecutionManagerTestFixture() {
    }

    static DatabaseExecutionManager manager(DatabaseAssemblyRunRepository repository,
                                            PersistenceRuntimeConfiguration configuration,
                                            ExecutorService flushExecutor,
                                            ScheduledExecutorService scheduler) {
        return DatabaseExecutionManager.builder()
                .repository(repository)
                .configuration(configuration)
                .autoCreateTables(false)
                .flushExecutor(flushExecutor)
                .maintenanceExecutor(scheduler)
                .build();
    }

    static PersistenceRuntimeConfiguration configurationOf(DatabaseExecutionManager manager)
            throws ReflectiveOperationException {
        Field field = DatabaseExecutionManager.class.getDeclaredField("configuration");
        field.setAccessible(true);
        return (PersistenceRuntimeConfiguration) field.get(manager);
    }

    static void awaitShutdownAdmissionClosure(DatabaseExecutionManager manager) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (manager.isAlive() && System.nanoTime() < deadline) {
            yieldToAsyncWork();
        }
        assertThat(manager.isAlive()).as("shutdown must close operation admission before waiting").isFalse();
    }

    static void awaitCompletedFlushes(DatabaseExecutionManager manager, long expectedCompletedFlushes)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        PersistenceRuntimeStats stats;
        do {
            stats = manager.snapshotStats();
            if (stats.completedFlushes() >= expectedCompletedFlushes) {
                return;
            }
            yieldToAsyncWork();
        } while (System.nanoTime() < deadline);

        assertThat(stats.completedFlushes()).isGreaterThanOrEqualTo(expectedCompletedFlushes);
    }

    static void awaitStats(DatabaseExecutionManager manager,
                           long expectedFailedFlushes,
                           int expectedBufferedLogs)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        PersistenceRuntimeStats stats;
        do {
            stats = manager.snapshotStats();
            if (stats.failedFlushes() == expectedFailedFlushes
                    && stats.bufferedStationLogs() == expectedBufferedLogs) {
                return;
            }
            yieldToAsyncWork();
        } while (System.nanoTime() < deadline);

        assertThat(stats.failedFlushes()).isEqualTo(expectedFailedFlushes);
        assertThat(stats.bufferedStationLogs()).as("failed flush must not lose the drained records")
                .isEqualTo(expectedBufferedLogs);
    }

    private static void yieldToAsyncWork() throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException("interrupted while awaiting persistence statistics");
        }
        Thread.yield();
    }

    static StationLogRecord stationRecord(UUID runId) {
        return new StationLogRecord(UUID.randomUUID(), runId, "station", null, StationLogStatus.SUCCEEDED,
                Instant.now(), Instant.now(), null, null, Map.of(), null);
    }

    static void assertMetadataOnly(AssemblyRunRecord record, String secret) {
        assertThat(record.context()).isEmpty();
        assertThat(record.inputParams()).isNull();
        assertThat(record.result()).isNull();
        assertThat(record.errorMessage()).isNull();
        assertThat(record.toString()).doesNotContain(secret);
    }

    static final class CapturingJsonCodec implements PersistenceJsonCodec {
        private final List<Object> serializedValues = new ArrayList<>();

        @Override
        public String toJson(Object value) {
            serializedValues.add(value);
            return "{}";
        }

        @Override
        public <T> T fromJson(String json, Class<T> type) {
            return null;
        }

        @Override
        public <T> T fromJson(String json, TypeReference<T> type) {
            return null;
        }

        List<Object> serializedValues() {
            return List.copyOf(serializedValues);
        }
    }

    static final class MutablePayload {
        private final List<String> values;

        MutablePayload(String value) {
            this.values = new ArrayList<>(List.of(value));
        }

        MutablePayload(List<String> values) {
            this.values = new ArrayList<>(values);
        }

        List<String> values() {
            return values;
        }
    }

    static final class MutablePayloadCloner implements PayloadCloner {
        @Override
        @SuppressWarnings("unchecked")
        public <T> T clonePayload(T payload) {
            if (payload instanceof MutablePayload mutablePayload) {
                return (T) new MutablePayload(mutablePayload.values());
            }
            return payload;
        }
    }
}
