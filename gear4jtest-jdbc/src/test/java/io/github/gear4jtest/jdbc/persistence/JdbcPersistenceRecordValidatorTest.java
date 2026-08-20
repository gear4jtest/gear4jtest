package io.github.gear4jtest.jdbc.persistence;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import javax.sql.DataSource;

import io.github.gear4jtest.core.execution.trace.AssemblyRunTrace;
import io.github.gear4jtest.core.model.StationLogStatus;
import io.github.gear4jtest.core.persistence.AssemblyRunRecord;
import io.github.gear4jtest.core.persistence.ExecutionStatus;
import io.github.gear4jtest.core.persistence.StationLogRecord;
import io.github.gear4jtest.jdbc.execution.DatabaseExecutionManager;
import io.github.gear4jtest.jdbc.execution.PersistenceRuntimeConfiguration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class JdbcPersistenceRecordValidatorTest {
    @Test
    void identifierLimit_shouldCountUnicodeCodePointsRatherThanUtf16Units() {
        AssemblyRunRecord accepted = runRecord("\uD83D\uDE80".repeat(255));

        JdbcPersistenceRecordValidator.validate(accepted);

        assertThatThrownBy(() -> JdbcPersistenceRecordValidator.validate(runRecord("a".repeat(256))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("assemblyLineId")
                .hasMessageContaining("255");
    }

    @Test
    void stationLogValidation_shouldCoverOperationBranchAndItemIdentifiers() {
        Instant now = Instant.now();
        UUID runId = UUID.randomUUID();

        assertThatThrownBy(() -> JdbcPersistenceRecordValidator.validate(new StationLogRecord(
                UUID.randomUUID(), runId, "a".repeat(256), null, null, StationLogStatus.RUNNING, now, null,
                null, null, Map.of(), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("operationId");
        assertThatThrownBy(() -> JdbcPersistenceRecordValidator.validate(new StationLogRecord(
                UUID.randomUUID(), runId, "operation", null, "b".repeat(256), StationLogStatus.RUNNING, now, null,
                null, null, Map.of(), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("branchId");
        assertThatThrownBy(() -> JdbcPersistenceRecordValidator.validate(new StationLogRecord(
                UUID.randomUUID(), runId, "operation", null, null, StationLogStatus.RUNNING, now, null,
                null, null, Map.of(), "i".repeat(256))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("itemId");
    }

    @Test
    void managerStart_shouldRejectInvalidIdentifierBeforeRepositoryOrBufferAdmission() {
        DatabaseAssemblyRunRepository repository = mock(DatabaseAssemblyRunRepository.class);
        ExecutorService flushExecutor = Executors.newSingleThreadExecutor();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        DatabaseExecutionManager manager = DatabaseExecutionManager.builder()
                .repository(repository)
                .configuration(PersistenceRuntimeConfiguration.defaults())
                .flushExecutor(flushExecutor)
                .maintenanceExecutor(scheduler)
                .build();
        AssemblyRunTrace trace = new AssemblyRunTrace(UUID.randomUUID(), "a".repeat(256), Map.of());
        try {
            assertThatThrownBy(() -> manager.start(trace))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("assemblyLineId");
            assertThat(manager.snapshotStats().activeRuns()).isZero();
            verifyNoInteractions(repository);
        } finally {
            manager.shutdown(Duration.ofSeconds(1));
            flushExecutor.shutdownNow();
            scheduler.shutdownNow();
        }
    }

    @Test
    void repositoryWrites_shouldValidateBeforeInteractingWithTheDataSource() {
        DataSource dataSource = mock(DataSource.class);
        DatabaseAssemblyRunRepository repository = DatabaseAssemblyRunRepository.builder()
                .dataSource(dataSource)
                .databaseDialect(Gear4jDatabaseDialect.H2)
                .build();
        Instant now = Instant.now();
        StationLogRecord invalidLog = new StationLogRecord(UUID.randomUUID(), UUID.randomUUID(), "a".repeat(256),
                null, null, StationLogStatus.RUNNING, now, null, null, null, Map.of(), null);

        assertThatThrownBy(() -> repository.save(runRecord("a".repeat(256))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.saveOperationRecord(invalidLog))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(dataSource);
    }

    private static AssemblyRunRecord runRecord(String assemblyLineId) {
        Instant now = Instant.now();
        UUID runId = UUID.randomUUID();
        return new AssemblyRunRecord(runId, assemblyLineId, Map.of(), null, null, ExecutionStatus.RUNNING,
                now, null, null, null, runId, null);
    }
}
